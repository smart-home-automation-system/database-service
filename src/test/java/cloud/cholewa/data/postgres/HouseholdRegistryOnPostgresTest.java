package cloud.cholewa.data.postgres;

import cloud.cholewa.data.error.HouseholdException;
import cloud.cholewa.data.error.MemberDeviceException;
import cloud.cholewa.data.household.model.HouseholdMemberEntity;
import cloud.cholewa.data.household.model.MemberDeviceEntity;
import cloud.cholewa.data.household.repository.HouseholdMemberRepository;
import cloud.cholewa.data.household.repository.MemberDeviceRepository;
import cloud.cholewa.data.household.service.HouseholdMemberService;
import cloud.cholewa.data.household.service.MemberDeviceService;
import cloud.cholewa.home.model.HouseholdMember;
import cloud.cholewa.home.model.HouseholdProfile;
import cloud.cholewa.home.model.MemberPermission;
import cloud.cholewa.home.model.MemberPhoneDetails;
import cloud.cholewa.home.model.MemberRole;
import cloud.cholewa.home.model.RoomName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

//The household registry on a real PostgreSQL: what Spring Data and the driver do with the two
//array columns, what a write leaves of the columns it does not change, and what the database says
//when a rule of the schema is broken - the text the services read the constraint from. Every test
//of the services themselves runs on mocked repositories and can show none of this.
class HouseholdRegistryOnPostgresTest extends OnPostgres {

    private static final String ANNA_PHONE = "+48111222333";
    private static final String BORYS_PHONE = "+48444555666";

    //neither the order of the alphabet nor the order of the constants in the enum
    private static final List<RoomName> ROOMS = List.of(RoomName.OFFICE, RoomName.LIVING_ROOM, RoomName.LOFT);

    private static final MemberPhoneDetails IPHONE = MemberPhoneDetails.builder()
        .name("iPhone")
        .mac("aa:bb:cc:dd:ee:01")
        .build();

    @Autowired
    private HouseholdMemberService householdMemberService;

    @Autowired
    private MemberDeviceService memberDeviceService;

    @Autowired
    private HouseholdMemberRepository householdMemberRepository;

    @Autowired
    private MemberDeviceRepository memberDeviceRepository;

    @Autowired
    private WebTestClient webTestClient;

    // ---- the array columns and the enums

    @Test
    void should_store_the_role_the_rooms_and_the_permissions_as_the_names_of_their_constants() {
        add(anna());

        final Map<String, Object> stored = storedMember("Anna");

        assertThat(stored)
            .containsEntry("role", "ADMIN")
            .containsEntry("rooms", "OFFICE,LIVING_ROOM,LOFT")
            .containsEntry("permissions", "HEATING_SWITCH")
            .containsEntry("active", true);
    }

    @Test
    void should_read_the_rooms_back_in_the_order_they_were_given() {
        add(anna());

        householdMemberRepository.findByNameIgnoreCase("Anna").as(StepVerifier::create)
            .assertNext(member -> {
                assertThat(member.getRole()).isEqualTo(MemberRole.ADMIN);
                assertThat(member.getRooms()).containsExactlyElementsOf(ROOMS);
                assertThat(member.getPermissions()).containsExactly(MemberPermission.HEATING_SWITCH);
            })
            .verifyComplete();
    }

    @Test
    void should_store_no_rooms_and_no_permissions_as_empty_arrays_and_read_them_as_empty_lists() {
        add(HouseholdMember.builder().name("Borys").phone(BORYS_PHONE).active(true).build());

        assertThat(row("""
            SELECT rooms = '{}' AS no_rooms, permissions = '{}' AS no_permissions, role
            FROM household_members WHERE name = 'Borys'"""))
            .containsEntry("no_rooms", true)
            .containsEntry("no_permissions", true)
            //a member registered without a role is a resident - decided by the mapper, the default
            //of the column never applies
            .containsEntry("role", "RESIDENT");

        householdMemberRepository.findByNameIgnoreCase("Borys").as(StepVerifier::create)
            .assertNext(member -> {
                assertThat(member.getRooms()).isNotNull().isEmpty();
                assertThat(member.getPermissions()).isNotNull().isEmpty();
            })
            .verifyComplete();
    }

    //the rows of the members who were there before V11 and V12 look like this one: the three
    //columns filled by their defaults
    @Test
    void should_read_a_row_that_holds_the_defaults_of_the_columns() {
        execute("""
            INSERT INTO household_members (created_at, name, phone)
            VALUES (now(), 'Celina', '+48777888999')""");

        householdMemberRepository.findByNameIgnoreCase("Celina").as(StepVerifier::create)
            .assertNext(member -> {
                assertThat(member.isActive()).isTrue();
                assertThat(member.getRole()).isEqualTo(MemberRole.RESIDENT);
                assertThat(member.getRooms()).isEmpty();
                assertThat(member.getPermissions()).isEmpty();
            })
            .verifyComplete();
    }

    //what the documentation of the SDK promises of a constant renamed or removed there: the row
    //that still holds the old name cannot be read, and with it the whole registry
    @Test
    void should_fail_to_read_the_registry_when_a_row_holds_a_room_the_enum_does_not_know() {
        add(anna());
        execute("UPDATE household_members SET rooms = '{OFFICE,ATTIC}' WHERE name = 'Anna'");

        householdMemberService.getAllHouseholdMembers().as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error).hasStackTraceContaining("ATTIC"));
    }

    // ---- a write keeps what it does not change

    @Test
    void should_change_the_name_and_the_phone_and_keep_every_other_column() {
        add(anna());
        deactivate("Anna");
        final Map<String, Object> before = storedMember("Anna");

        householdMemberService.updateHouseholdMember(
                "Anna", HouseholdMember.builder().name("Anka").phone(BORYS_PHONE).active(true).build())
            .as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();

        assertThat(storedMember("Anka"))
            .containsEntry("phone", BORYS_PHONE)
            //the body said active, without a role, rooms or permissions - none of it is honoured
            .containsEntry("active", false)
            .satisfies(after -> assertUnchanged(before, after, "id", "created_at", "role", "rooms", "permissions"));
        assertOneMember();
    }

    @Test
    void should_change_the_role_only_when_the_update_names_one() {
        add(anna());

        householdMemberService.updateHouseholdMember("Anna", HouseholdMember.builder()
                .name("Anna").phone(ANNA_PHONE).active(true).role(MemberRole.RESIDENT).build())
            .as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();

        assertThat(storedMember("Anna"))
            .containsEntry("role", "RESIDENT")
            //still active: a mapper that lost the column would write false, the value of a boolean
            //nobody set - which the test above, on a member who is switched off, cannot see
            .containsEntry("active", true)
            .containsEntry("rooms", "OFFICE,LIVING_ROOM,LOFT")
            .containsEntry("permissions", "HEATING_SWITCH");
    }

    @Test
    void should_replace_the_rooms_and_keep_every_other_column() {
        add(anna());
        deactivate("Anna");
        final Map<String, Object> before = storedMember("Anna");

        householdMemberService.replaceRooms("Anna", List.of(RoomName.KITCHEN, RoomName.CINEMA))
            .as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();

        assertThat(storedMember("Anna"))
            .containsEntry("rooms", "KITCHEN,CINEMA")
            .satisfies(after -> assertUnchanged(
                before, after, "id", "created_at", "name", "phone", "active", "role", "permissions"));
        assertOneMember();
    }

    @Test
    void should_take_every_room_away_with_an_empty_list() {
        add(anna());

        householdMemberService.replaceRooms("Anna", List.of())
            .as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();

        assertThat(storedMember("Anna"))
            .containsEntry("rooms", "")
            //an active member stays active, see should_change_the_role_only_when_the_update_names_one
            .containsEntry("active", true)
            .containsEntry("permissions", "HEATING_SWITCH");
    }

    @Test
    void should_replace_the_permissions_and_keep_every_other_column() {
        add(anna());
        final Map<String, Object> active = storedMember("Anna");

        householdMemberService.replacePermissions("Anna", List.of())
            .as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();

        assertThat(storedMember("Anna"))
            .containsEntry("permissions", "")
            .containsEntry("active", true)
            .satisfies(after -> assertUnchanged(
                active, after, "id", "created_at", "name", "phone", "active", "role", "rooms"));

        //and once more for a member who is switched off: neither state of the activity may be the
        //one the operation writes by itself
        deactivate("Anna");
        final Map<String, Object> switchedOff = storedMember("Anna");

        householdMemberService.replacePermissions("Anna", List.of(MemberPermission.HEATING_SWITCH))
            .as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();

        assertThat(storedMember("Anna"))
            .containsEntry("permissions", "HEATING_SWITCH")
            .containsEntry("active", false)
            .satisfies(after -> assertUnchanged(
                switchedOff, after, "id", "created_at", "name", "phone", "active", "role", "rooms"));
        assertOneMember();
    }

    @Test
    void should_switch_a_member_off_and_on_and_keep_every_other_column() {
        add(anna());
        final Map<String, Object> before = storedMember("Anna");

        deactivate("Anna");

        assertThat(storedMember("Anna"))
            .containsEntry("active", false)
            .satisfies(after -> assertUnchanged(
                before, after, "id", "created_at", "name", "phone", "role", "rooms", "permissions"));

        householdMemberService.activateHouseholdMember("Anna").as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();

        assertThat(storedMember("Anna"))
            .containsEntry("active", true)
            .satisfies(after -> assertUnchanged(
                before, after, "id", "created_at", "name", "phone", "role", "rooms", "permissions"));
        assertOneMember();
    }

    @Test
    void should_change_a_device_and_keep_its_owner() {
        add(anna());
        addDevice("Anna", IPHONE);
        final Map<String, Object> before = row("SELECT id, member_id, created_at FROM member_devices");

        memberDeviceService.updateDevice(
                "Anna", "AA:BB:CC:DD:EE:01", MemberPhoneDetails.builder().name("Pixel").mac("aa:bb:cc:dd:ee:02").build())
            .as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();

        assertThat(row("SELECT id, member_id, created_at, name, mac, updated_at IS NOT NULL AS updated FROM member_devices"))
            .containsEntry("name", "Pixel")
            .containsEntry("mac", "aa:bb:cc:dd:ee:02")
            .containsEntry("updated", true)
            .satisfies(after -> assertUnchanged(before, after, "id", "member_id", "created_at"));
    }

    // ---- the reads

    @Test
    void should_read_the_whole_registry_with_the_devices_of_every_member() {
        add(HouseholdMember.builder().name("Borys").phone(BORYS_PHONE).active(true).build());
        add(admin("anna"));
        addDevice("anna", IPHONE);
        addDevice("anna", MemberPhoneDetails.builder().name("Watch").mac("aa:bb:cc:dd:ee:03").build());
        addDevice("Borys", MemberPhoneDetails.builder().name("iPhone").mac("aa:bb:cc:dd:ee:04").build());

        householdMemberService.getAllHouseholdMembers().as(StepVerifier::create)
            .assertNext(members -> {
                //whatever the letter case: by the natural order of strings "Borys" would come first
                assertThat(members).extracting(HouseholdMember::getName).containsExactly("anna", "Borys");
                assertThat(members.getFirst().getRooms()).containsExactlyElementsOf(ROOMS);
                //by name, capitals first
                assertThat(members.getFirst().getDevices())
                    .extracting(MemberPhoneDetails::getMac)
                    .containsExactly("aa:bb:cc:dd:ee:03", "aa:bb:cc:dd:ee:01");
                assertThat(members.getLast().getDevices())
                    .extracting(MemberPhoneDetails::getMac)
                    .containsExactly("aa:bb:cc:dd:ee:04");
            })
            .verifyComplete();
    }

    @Test
    void should_answer_an_empty_registry_with_an_empty_list() {
        householdMemberService.getAllHouseholdMembers().as(StepVerifier::create)
            .expectNext(List.of())
            .verifyComplete();
    }

    @Test
    void should_answer_the_profiles_of_the_active_members_only() {
        add(anna());
        add(HouseholdMember.builder().name("Borys").phone(BORYS_PHONE).active(true).build());
        deactivate("Borys");

        householdMemberService.getHouseholdProfiles().as(StepVerifier::create)
            .assertNext(profiles -> assertThat(profiles).containsExactly(HouseholdProfile.builder()
                .name("Anna")
                .role(MemberRole.ADMIN)
                .rooms(ROOMS)
                .permissions(List.of(MemberPermission.HEATING_SWITCH))
                .build()))
            .verifyComplete();
    }

    @Test
    void should_find_a_member_by_the_name_in_any_letter_case() {
        add(anna());

        householdMemberRepository.findByNameIgnoreCase("aNNa").as(StepVerifier::create)
            .assertNext(member -> assertThat(member.getName()).isEqualTo("Anna"))
            .verifyComplete();
        householdMemberRepository.existsByNameIgnoreCase("ANNA").as(StepVerifier::create)
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    void should_remove_the_devices_of_a_member_with_the_member() {
        add(anna());
        addDevice("Anna", IPHONE);

        householdMemberService.removeHouseholdMember("Anna").as(StepVerifier::create).verifyComplete();

        memberDeviceRepository.count().as(StepVerifier::create).expectNext(0L).verifyComplete();
    }

    // ---- the rules of the schema, and what the services make of them

    @Test
    void should_refuse_a_phone_number_another_member_has() {
        add(anna());

        householdMemberService.addHouseholdMember(
                HouseholdMember.builder().name("Borys").phone(ANNA_PHONE).active(true).build())
            .as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(HouseholdException.class)
                .hasMessage("Phone number [" + ANNA_PHONE + "] is already assigned to another household member"));
    }

    //the check before the insert never sees this one: only the index on upper(name) can refuse it
    @Test
    void should_refuse_to_rename_a_member_to_the_name_of_another_in_any_letter_case() {
        add(anna());
        add(HouseholdMember.builder().name("Borys").phone(BORYS_PHONE).active(true).build());

        householdMemberService.updateHouseholdMember(
                "Borys", HouseholdMember.builder().name("ANNA").phone(BORYS_PHONE).active(true).build())
            .as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(HouseholdException.class)
                .hasMessage("Household member named [ANNA] already exists"));
    }

    @Test
    void should_change_the_letter_case_of_a_members_own_name() {
        add(anna());

        householdMemberService.updateHouseholdMember(
                "Anna", HouseholdMember.builder().name("ANNA").phone(ANNA_PHONE).active(true).build())
            .as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();

        assertThat(storedMember("ANNA")).containsEntry("rooms", "OFFICE,LIVING_ROOM,LOFT");
        assertOneMember();
    }

    @Test
    void should_refuse_a_mac_address_that_is_registered_whoever_has_it() {
        add(anna());
        add(HouseholdMember.builder().name("Borys").phone(BORYS_PHONE).active(true).build());
        addDevice("Anna", IPHONE);

        memberDeviceService.addDevice("Borys", MemberPhoneDetails.builder().name("Tablet").mac(IPHONE.getMac()).build())
            .as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(MemberDeviceException.class)
                .hasMessage("Device with MAC [aa:bb:cc:dd:ee:01] is already registered"));
    }

    @Test
    void should_refuse_a_second_device_of_the_same_name_for_one_member_and_allow_it_for_another() {
        add(anna());
        add(HouseholdMember.builder().name("Borys").phone(BORYS_PHONE).active(true).build());
        addDevice("Anna", IPHONE);

        memberDeviceService.addDevice("Anna", MemberPhoneDetails.builder().name("iPhone").mac("aa:bb:cc:dd:ee:05").build())
            .as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(MemberDeviceException.class)
                .hasMessage("Household member [Anna] already has a device named [iPhone]"));

        addDevice("Borys", MemberPhoneDetails.builder().name("iPhone").mac("aa:bb:cc:dd:ee:06").build());
    }

    //The services tell the duplicates apart by the name of the constraint in the message of the
    //exception, so the name and the type of the exception are what a bump of Spring Data or of the
    //driver must not change. Asked of the repositories, without a service in between
    @Test
    void should_name_the_broken_unique_constraint_in_a_duplicate_key_exception() {
        add(anna());
        addDevice("Anna", IPHONE);
        final Long annaId = ((Number) storedMember("Anna").get("id")).longValue();

        householdMemberRepository.save(member("anna", BORYS_PHONE)).as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertDuplicate(error, "household_members_name_upper_uq"));
        householdMemberRepository.save(member("Borys", ANNA_PHONE)).as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertDuplicate(error, "household_members_phone_uq"));
        memberDeviceRepository.save(device(annaId, "Tablet", IPHONE.getMac())).as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertDuplicate(error, "member_devices_mac_uq"));
        memberDeviceRepository.save(device(annaId, "iPhone", "aa:bb:cc:dd:ee:07")).as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertDuplicate(error, "member_devices_member_name_uq"));
    }

    //what the schema refuses although no code of the service could send it: a row is written by
    //hand, or by a version that has a fault
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        role = 'OWNER'                           | household_members_role_known
        rooms = ARRAY['OFFICE', NULL]            | household_members_rooms_no_null
        permissions = ARRAY[NULL]::TEXT[]        | household_members_permissions_no_null
        phone = '505-602-702'                    | household_members_phone_format
        """)
    void should_refuse_a_row_that_breaks_a_check_of_the_schema(final String change, final String constraint) {
        add(anna());

        databaseClient.sql("UPDATE household_members SET " + change + " WHERE name = 'Anna'").then()
            .as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining(constraint));
    }

    // ---- and the whole way, from the request to the answer

    @Test
    void should_answer_a_conflict_with_its_code_when_the_database_refuses_a_duplicate() {
        add(anna());
        addDevice("Anna", IPHONE);

        webTestClient.post()
            .uri("/household/member")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"name": "Borys", "phone": "%s", "active": true}""".formatted(ANNA_PHONE))
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("HOUSEHOLD_CONFLICT")
            .jsonPath("$.errors[0].details")
            .isEqualTo("Phone number [" + ANNA_PHONE + "] is already assigned to another household member");

        webTestClient.post()
            .uri("/household/member/Anna/device")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"name": "Tablet", "mac": "aa:bb:cc:dd:ee:01"}""")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("DEVICE_EXIST");
    }

    @Test
    void should_register_a_member_and_serve_the_registry_in_the_words_of_the_api() {
        webTestClient.post()
            .uri("/household/member")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"name": "Anna", "phone": "%s", "active": true, "role": "admin",
                 "rooms": ["office", "living room", "loft"], "permissions": ["heating_switch"]}"""
                .formatted(ANNA_PHONE))
            .exchange()
            .expectStatus().is2xxSuccessful();

        webTestClient.get()
            .uri("/household")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].role").isEqualTo("admin")
            .jsonPath("$[0].rooms").isEqualTo(List.of("office", "living room", "loft"))
            .jsonPath("$[0].permissions").isEqualTo(List.of("heating_switch"));

        assertThat(storedMember("Anna")).containsEntry("rooms", "OFFICE,LIVING_ROOM,LOFT");
    }

    // ---- helpers

    private static HouseholdMember anna() {
        return admin("Anna");
    }

    private static HouseholdMember admin(final String name) {
        return HouseholdMember.builder()
            .name(name)
            .phone(ANNA_PHONE)
            .active(true)
            .role(MemberRole.ADMIN)
            .rooms(ROOMS)
            .permissions(List.of(MemberPermission.HEATING_SWITCH))
            .build();
    }

    private static HouseholdMemberEntity member(final String name, final String phone) {
        return new HouseholdMemberEntity(
            null, LocalDateTime.of(2026, 10, 11, 12, 0), null, name, phone, true, MemberRole.RESIDENT, List.of(), List.of());
    }

    private static MemberDeviceEntity device(final Long memberId, final String name, final String mac) {
        return new MemberDeviceEntity(null, LocalDateTime.of(2026, 10, 11, 12, 0), null, memberId, name, mac);
    }

    private void add(final HouseholdMember member) {
        householdMemberService.addHouseholdMember(member).as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();
    }

    private void addDevice(final String memberName, final MemberPhoneDetails device) {
        memberDeviceService.addDevice(memberName, device).as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();
    }

    private void deactivate(final String name) {
        householdMemberService.deactivateHouseholdMember(name).as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();
    }

    //the row as the database holds it, the arrays written out - not as an entity, which would put
    //the mapping under test on both sides of the comparison
    private Map<String, Object> storedMember(final String name) {
        return row("""
            SELECT id, created_at, name, phone, active, role,
                   array_to_string(rooms, ',') AS rooms,
                   array_to_string(permissions, ',') AS permissions
            FROM household_members WHERE name = '%s'""".formatted(name));
    }

    private void assertOneMember() {
        assertThat(row("SELECT count(*) AS members FROM household_members")).containsEntry("members", 1L);
    }

    private static void assertUnchanged(
        final Map<String, Object> before,
        final Map<String, Object> after,
        final String... columns
    ) {
        for (final String column : columns) {
            assertThat(after.get(column)).as(column).isNotNull().isEqualTo(before.get(column));
        }
    }

    private static void assertDuplicate(final Throwable error, final String constraint) {
        assertThat(error)
            .isInstanceOf(DuplicateKeyException.class)
            .hasMessageContaining(constraint);
    }
}
