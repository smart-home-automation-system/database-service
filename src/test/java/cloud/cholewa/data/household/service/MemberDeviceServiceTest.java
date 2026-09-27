package cloud.cholewa.data.household.service;

import cloud.cholewa.data.error.HouseholdMemberNotFoundException;
import cloud.cholewa.data.error.MemberDeviceException;
import cloud.cholewa.data.error.MemberDeviceNotFoundException;
import cloud.cholewa.data.household.mapper.MemberDeviceMapper;
import cloud.cholewa.data.household.mapper.MemberDeviceMapperImpl;
import cloud.cholewa.data.household.model.HouseholdMemberEntity;
import cloud.cholewa.data.household.model.MemberDeviceEntity;
import cloud.cholewa.data.household.repository.HouseholdMemberRepository;
import cloud.cholewa.data.household.repository.MemberDeviceRepository;
import cloud.cholewa.home.model.MemberPhoneDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberDeviceServiceTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 15, 12, 0);

    private static final HouseholdMemberEntity MEMBER = new HouseholdMemberEntity(
        7L, CREATED_AT, null, "Ola", "111-222-333", true
    );

    private static final MemberDeviceEntity STORED_DEVICE = new MemberDeviceEntity(
        3L, CREATED_AT, null, 7L, "iPhone", "aa:bb:cc:dd:ee:ff"
    );

    private static final MemberPhoneDetails DEVICE = MemberPhoneDetails.builder()
        .name("iPad")
        .mac("11:22:33:44:55:66")
        .build();

    @Mock
    private HouseholdMemberRepository memberRepository;

    @Mock
    private MemberDeviceRepository deviceRepository;

    //the real generated mapper - whether an update keeps the stored id and owner is exactly what is under test
    @Spy
    private MemberDeviceMapper mapper = new MemberDeviceMapperImpl();

    @InjectMocks
    private MemberDeviceService sut;

    @Test
    void should_add_device_to_member() {
        when(memberRepository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(MEMBER));
        when(deviceRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        sut.addDevice("Ola", DEVICE)
            .as(StepVerifier::create)
            .assertNext(added -> {
                assertThat(added.getName()).isEqualTo("iPad");
                assertThat(added.getMac()).isEqualTo("11:22:33:44:55:66");
            })
            .verifyComplete();

        final MemberDeviceEntity saved = savedDevice();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getMemberId()).isEqualTo(7L);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void should_not_add_device_to_unknown_member() {
        when(memberRepository.findByNameIgnoreCase(anyString())).thenReturn(Mono.empty());

        sut.addDevice("Nobody", DEVICE)
            .as(StepVerifier::create)
            .expectErrorMatches(throwable -> throwable instanceof HouseholdMemberNotFoundException
                && throwable.getMessage().equals("No household member named [Nobody]"))
            .verify();

        verifyNoInteractions(deviceRepository);
    }

    @Test
    void should_report_mac_registered_already() {
        when(memberRepository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(MEMBER));
        when(deviceRepository.save(any())).thenReturn(Mono.error(duplicateKey("member_devices_mac_uq")));

        sut.addDevice("Ola", DEVICE)
            .as(StepVerifier::create)
            .expectErrorMatches(throwable -> throwable instanceof MemberDeviceException
                && throwable.getMessage().equals("Device with MAC [11:22:33:44:55:66] is already registered"))
            .verify();
    }

    @Test
    void should_report_device_name_taken_for_member() {
        when(memberRepository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(MEMBER));
        when(deviceRepository.save(any())).thenReturn(Mono.error(duplicateKey("member_devices_member_name_uq")));

        sut.addDevice("Ola", DEVICE)
            .as(StepVerifier::create)
            .expectErrorMatches(throwable -> throwable instanceof MemberDeviceException
                && throwable.getMessage().equals("Household member [Ola] already has a device named [iPad]"))
            .verify();
    }

    @Test
    void should_pass_through_duplicate_key_of_unknown_constraint() {
        when(memberRepository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(MEMBER));
        when(deviceRepository.save(any())).thenReturn(Mono.error(duplicateKey("some_other_uq")));

        sut.addDevice("Ola", DEVICE)
            .as(StepVerifier::create)
            .expectError(DuplicateKeyException.class)
            .verify();
    }

    @Test
    void should_remove_device_found_by_mac_written_in_uppercase() {
        when(memberRepository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(MEMBER));
        when(deviceRepository.findByMemberIdAndMac(7L, "aa:bb:cc:dd:ee:ff")).thenReturn(Mono.just(STORED_DEVICE));
        when(deviceRepository.delete(STORED_DEVICE)).thenReturn(Mono.empty());

        sut.removeDevice("Ola", "AA:BB:CC:DD:EE:FF")
            .as(StepVerifier::create)
            .verifyComplete();

        verify(deviceRepository).delete(STORED_DEVICE);
    }

    @Test
    void should_return_error_when_member_has_no_such_device() {
        when(memberRepository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(MEMBER));
        when(deviceRepository.findByMemberIdAndMac(anyLong(), anyString())).thenReturn(Mono.empty());

        sut.removeDevice("Ola", "aa:bb:cc:dd:ee:ff")
            .as(StepVerifier::create)
            .expectErrorMatches(throwable -> throwable instanceof MemberDeviceNotFoundException
                && throwable.getMessage().equals("Household member [Ola] has no device with MAC [aa:bb:cc:dd:ee:ff]"))
            .verify();

        verify(deviceRepository, never()).delete(any());
    }

    @Test
    void should_return_error_when_removing_device_of_unknown_member() {
        when(memberRepository.findByNameIgnoreCase(anyString())).thenReturn(Mono.empty());

        sut.removeDevice("Nobody", "aa:bb:cc:dd:ee:ff")
            .as(StepVerifier::create)
            .expectError(HouseholdMemberNotFoundException.class)
            .verify();

        verifyNoInteractions(deviceRepository);
    }

    @Test
    void should_update_stored_device_instead_of_inserting_a_new_one() {
        when(memberRepository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(MEMBER));
        when(deviceRepository.findByMemberIdAndMac(7L, "aa:bb:cc:dd:ee:ff")).thenReturn(Mono.just(STORED_DEVICE));
        when(deviceRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        sut.updateDevice("Ola", "aa:bb:cc:dd:ee:ff", DEVICE)
            .as(StepVerifier::create)
            .assertNext(updated -> assertThat(updated.getName()).isEqualTo("iPad"))
            .verifyComplete();

        final MemberDeviceEntity saved = savedDevice();
        assertThat(saved.getId()).isEqualTo(3L);
        assertThat(saved.getMemberId()).isEqualTo(7L);
        assertThat(saved.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getName()).isEqualTo("iPad");
        assertThat(saved.getMac()).isEqualTo("11:22:33:44:55:66");
    }

    @Test
    void should_report_mac_registered_already_when_updating_device() {
        when(memberRepository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(MEMBER));
        when(deviceRepository.findByMemberIdAndMac(7L, "aa:bb:cc:dd:ee:ff")).thenReturn(Mono.just(STORED_DEVICE));
        when(deviceRepository.save(any())).thenReturn(Mono.error(duplicateKey("member_devices_mac_uq")));

        sut.updateDevice("Ola", "aa:bb:cc:dd:ee:ff", DEVICE)
            .as(StepVerifier::create)
            .expectError(MemberDeviceException.class)
            .verify();
    }

    @Test
    void should_return_error_when_updating_unknown_device() {
        when(memberRepository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(MEMBER));
        when(deviceRepository.findByMemberIdAndMac(anyLong(), anyString())).thenReturn(Mono.empty());

        sut.updateDevice("Ola", "aa:bb:cc:dd:ee:ff", DEVICE)
            .as(StepVerifier::create)
            .expectError(MemberDeviceNotFoundException.class)
            .verify();

        verify(deviceRepository, never()).save(any());
    }

    private MemberDeviceEntity savedDevice() {
        final ArgumentCaptor<MemberDeviceEntity> captor = ArgumentCaptor.forClass(MemberDeviceEntity.class);
        verify(deviceRepository).save(captor.capture());
        return captor.getValue();
    }

    //the shape Spring's R2DBC exception translation produces: the driver message names the constraint
    private static DuplicateKeyException duplicateKey(final String constraint) {
        return new DuplicateKeyException(
            "executeMany; SQL [INSERT INTO member_devices ...]; duplicate key value violates unique constraint \""
                + constraint + "\""
        );
    }
}
