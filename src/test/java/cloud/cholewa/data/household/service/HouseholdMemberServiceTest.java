package cloud.cholewa.data.household.service;

import cloud.cholewa.data.error.HouseholdException;
import cloud.cholewa.data.error.HouseholdMemberNotFoundException;
import cloud.cholewa.data.household.mapper.HouseholdMemberMapper;
import cloud.cholewa.data.household.mapper.HouseholdMemberMapperImpl;
import cloud.cholewa.data.household.mapper.MemberDeviceMapper;
import cloud.cholewa.data.household.mapper.MemberDeviceMapperImpl;
import cloud.cholewa.data.household.model.HouseholdMemberEntity;
import cloud.cholewa.data.household.model.MemberDeviceEntity;
import cloud.cholewa.data.household.repository.HouseholdMemberRepository;
import cloud.cholewa.data.household.repository.MemberDeviceRepository;
import cloud.cholewa.home.model.HouseholdMember;
import cloud.cholewa.home.model.MemberPhoneDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseholdMemberServiceTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 15, 12, 0);

    private static final HouseholdMemberEntity STORED = new HouseholdMemberEntity(
        7L, CREATED_AT, null, "Ola", "+48111222333", true
    );

    private static final MemberDeviceEntity DEVICE = new MemberDeviceEntity(
        1L, CREATED_AT, null, 7L, "iPhone", "aa:bb:cc:dd:ee:01"
    );

    private static final HouseholdMember MEMBER = HouseholdMember.builder()
        .name("Jan")
        .phone("+48444555666")
        .active(true)
        .build();

    @Mock
    private HouseholdMemberRepository repository;

    //the real generated mapper - whether an update keeps the stored id is exactly what is under test
    @Spy
    private HouseholdMemberMapper mapper = new HouseholdMemberMapperImpl();

    @Mock
    private MemberDeviceRepository deviceRepository;

    @Spy
    private MemberDeviceMapper deviceMapper = new MemberDeviceMapperImpl();

    @InjectMocks
    private HouseholdMemberService sut;

    @Test
    void should_return_members_sorted_by_name() {
        when(repository.findAll()).thenReturn(Flux.just(
            new HouseholdMemberEntity(2L, CREATED_AT, null, "Zenon", "+48999888777", true),
            STORED,
            new HouseholdMemberEntity(3L, CREATED_AT, null, "anna", "+48777666555", true)
        ));
        when(deviceRepository.findAll()).thenReturn(Flux.empty());

        //case-insensitive, like the names themselves: "anna" is not sorted after "Zenon"
        sut.getAllHouseholdMembers()
            .as(StepVerifier::create)
            .assertNext(members -> assertThat(members)
                .extracting(HouseholdMember::getName)
                .containsExactly("anna", "Ola", "Zenon"))
            .verifyComplete();
    }

    @Test
    void should_attach_devices_to_their_members() {
        when(repository.findAll()).thenReturn(Flux.just(
            STORED,
            new HouseholdMemberEntity(2L, CREATED_AT, null, "Zenon", "+48999888777", true)
        ));
        when(deviceRepository.findAll()).thenReturn(Flux.just(
            new MemberDeviceEntity(1L, CREATED_AT, null, 7L, "iPhone", "aa:bb:cc:dd:ee:01"),
            new MemberDeviceEntity(2L, CREATED_AT, null, 7L, "iPad", "aa:bb:cc:dd:ee:02")
        ));

        sut.getAllHouseholdMembers()
            .as(StepVerifier::create)
            .assertNext(members -> {
                assertThat(members.get(0).getName()).isEqualTo("Ola");
                assertThat(members.get(0).getDevices())
                    .extracting(MemberPhoneDetails::getName, MemberPhoneDetails::getMac)
                    .containsExactly(
                        tuple("iPad", "aa:bb:cc:dd:ee:02"),
                        tuple("iPhone", "aa:bb:cc:dd:ee:01")
                    );
                assertThat(members.get(1).getDevices()).isEmpty();
            })
            .verifyComplete();
    }

    @Test
    void should_return_empty_list_when_there_are_no_members() {
        when(repository.findAll()).thenReturn(Flux.empty());
        when(deviceRepository.findAll()).thenReturn(Flux.empty());

        sut.getAllHouseholdMembers()
            .as(StepVerifier::create)
            .assertNext(members -> assertThat(members).isEmpty())
            .verifyComplete();
    }

    @Test
    void should_add_member_as_active_new_row() {
        when(repository.existsByNameIgnoreCase("Jan")).thenReturn(Mono.just(false));
        when(repository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        sut.addHouseholdMember(HouseholdMember.builder()
                .name("Jan")
                .phone("+48444555666")
                .active(true)
                .devices(List.of(MemberPhoneDetails.builder().name("iPhone").mac("aa:bb:cc:dd:ee:09").build()))
                .build())
            .as(StepVerifier::create)
            .assertNext(added -> {
                //devices in the payload are ignored - a new member has none yet
                assertThat(added.getDevices()).isEmpty();
                assertThat(added.getName()).isEqualTo("Jan");
                assertThat(added.getPhone()).isEqualTo("+48444555666");
                assertThat(added.getActive()).isTrue();
            })
            .verifyComplete();

        final HouseholdMemberEntity saved = savedEntity();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void should_reject_member_whose_name_already_exists() {
        when(repository.existsByNameIgnoreCase("Jan")).thenReturn(Mono.just(true));

        sut.addHouseholdMember(MEMBER)
            .as(StepVerifier::create)
            .expectErrorMatches(throwable -> throwable instanceof HouseholdException
                && throwable.getMessage().equals("Household member named [Jan] already exists"))
            .verify();

        verify(repository, never()).save(any());
    }

    @Test
    void should_report_duplicated_phone_when_adding_member() {
        when(repository.existsByNameIgnoreCase("Jan")).thenReturn(Mono.just(false));
        when(repository.save(any())).thenReturn(Mono.error(duplicateKey("household_members_phone_uq")));

        sut.addHouseholdMember(MEMBER)
            .as(StepVerifier::create)
            .expectErrorMatches(throwable -> throwable instanceof HouseholdException
                && throwable.getMessage()
                .equals("Phone number [+48444555666] is already assigned to another household member"))
            .verify();
    }

    @Test
    void should_update_stored_row_instead_of_inserting_a_new_one() {
        when(repository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(STORED));
        when(repository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(deviceRepository.findAllByMemberId(7L)).thenReturn(Flux.just(DEVICE));

        sut.updateHouseholdMember("Ola", MEMBER)
            .as(StepVerifier::create)
            .assertNext(updated -> {
                assertThat(updated.getName()).isEqualTo("Jan");
                //the response carries the member's devices, like GET does
                assertThat(updated.getDevices()).extracting(MemberPhoneDetails::getMac).containsExactly("aa:bb:cc:dd:ee:01");
            })
            .verifyComplete();

        final HouseholdMemberEntity saved = savedEntity();
        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Jan");
        assertThat(saved.getPhone()).isEqualTo("+48444555666");
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void should_keep_member_inactive_when_updating_without_active() {
        when(repository.findByNameIgnoreCase("Ola"))
            .thenReturn(Mono.just(new HouseholdMemberEntity(7L, CREATED_AT, null, "Ola", "+48111222333", false)));
        when(repository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(deviceRepository.findAllByMemberId(7L)).thenReturn(Flux.empty());

        sut.updateHouseholdMember("Ola", MEMBER)
            .as(StepVerifier::create)
            .assertNext(updated -> assertThat(updated.getActive()).isFalse())
            .verifyComplete();
    }

    @Test
    void should_report_taken_name_when_a_concurrent_rename_hits_the_index() {
        when(repository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(STORED));
        when(repository.save(any())).thenReturn(Mono.error(duplicateKey("household_members_name_upper_uq")));

        sut.updateHouseholdMember("Ola", MEMBER)
            .as(StepVerifier::create)
            .expectErrorMatches(throwable -> throwable instanceof HouseholdException
                && throwable.getMessage().equals("Household member named [Jan] already exists"))
            .verify();
    }

    @Test
    void should_pass_through_duplicate_key_of_unknown_constraint() {
        when(repository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(STORED));
        when(repository.save(any())).thenReturn(Mono.error(duplicateKey("some_other_uq")));

        sut.updateHouseholdMember("Ola", MEMBER)
            .as(StepVerifier::create)
            .expectError(DuplicateKeyException.class)
            .verify();
    }

    @Test
    void should_reject_renaming_to_another_members_name_in_different_case() {
        //"anna" while "Anna" exists - the upper(name) index (V10) rejects it
        when(repository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(STORED));
        when(repository.save(any())).thenReturn(Mono.error(duplicateKey("household_members_name_upper_uq")));

        sut.updateHouseholdMember("Ola", HouseholdMember.builder().name("anna").phone("+48444555666").build())
            .as(StepVerifier::create)
            .expectErrorMatches(throwable -> throwable instanceof HouseholdException
                && throwable.getMessage().equals("Household member named [anna] already exists"))
            .verify();
    }

    @Test
    void should_allow_changing_only_the_case_of_the_own_name() {
        when(repository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(STORED));
        when(repository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(deviceRepository.findAllByMemberId(7L)).thenReturn(Flux.empty());

        sut.updateHouseholdMember("Ola", HouseholdMember.builder().name("OLA").phone("+48111222333").build())
            .as(StepVerifier::create)
            .assertNext(updated -> assertThat(updated.getName()).isEqualTo("OLA"))
            .verifyComplete();

        verify(repository, never()).existsByNameIgnoreCase(anyString());
    }

    @Test
    void should_return_error_when_updating_unknown_member() {
        when(repository.findByNameIgnoreCase(anyString())).thenReturn(Mono.empty());

        sut.updateHouseholdMember("Nobody", MEMBER)
            .as(StepVerifier::create)
            .expectErrorMatches(throwable -> throwable instanceof HouseholdMemberNotFoundException
                && throwable.getMessage().equals("No household member named [Nobody]"))
            .verify();

        verify(repository, never()).save(any());
    }

    @Test
    void should_remove_member() {
        when(repository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(STORED));
        when(repository.delete(STORED)).thenReturn(Mono.empty());

        sut.removeHouseholdMember("Ola")
            .as(StepVerifier::create)
            .verifyComplete();

        verify(repository).delete(STORED);
    }

    @Test
    void should_return_error_when_removing_unknown_member() {
        when(repository.findByNameIgnoreCase(anyString())).thenReturn(Mono.empty());

        sut.removeHouseholdMember("Nobody")
            .as(StepVerifier::create)
            .expectError(HouseholdMemberNotFoundException.class)
            .verify();

        verify(repository, never()).delete(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_change_activity_of_stored_row(final boolean active) {
        when(repository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(STORED));
        when(repository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(deviceRepository.findAllByMemberId(7L)).thenReturn(Flux.just(DEVICE));

        final Mono<HouseholdMember> result = active
            ? sut.activateHouseholdMember("Ola")
            : sut.deactivateHouseholdMember("Ola");

        result
            .as(StepVerifier::create)
            .assertNext(member -> {
                assertThat(member.getActive()).isEqualTo(active);
                assertThat(member.getDevices()).hasSize(1);
            })
            .verifyComplete();

        final HouseholdMemberEntity saved = savedEntity();
        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Ola");
        assertThat(saved.isActive()).isEqualTo(active);
    }

    @Test
    void should_return_error_when_deactivating_unknown_member() {
        when(repository.findByNameIgnoreCase(anyString())).thenReturn(Mono.empty());

        sut.deactivateHouseholdMember("Nobody")
            .as(StepVerifier::create)
            .expectError(HouseholdMemberNotFoundException.class)
            .verify();
    }

    private HouseholdMemberEntity savedEntity() {
        final ArgumentCaptor<HouseholdMemberEntity> captor = ArgumentCaptor.forClass(HouseholdMemberEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    //the shape Spring's R2DBC exception translation produces: the driver message names the constraint
    private static DuplicateKeyException duplicateKey(final String constraint) {
        return new DuplicateKeyException(
            "executeMany; SQL [INSERT INTO household_members ...]; duplicate key value violates unique constraint \""
                + constraint + "\""
        );
    }
}
