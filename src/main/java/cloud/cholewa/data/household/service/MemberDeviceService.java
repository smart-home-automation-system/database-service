package cloud.cholewa.data.household.service;

import cloud.cholewa.data.error.HouseholdMemberNotFoundException;
import cloud.cholewa.data.error.MemberDeviceException;
import cloud.cholewa.data.error.MemberDeviceNotFoundException;
import cloud.cholewa.data.household.mapper.MemberDeviceMapper;
import cloud.cholewa.data.household.model.HouseholdMemberEntity;
import cloud.cholewa.data.household.model.MemberDeviceEntity;
import cloud.cholewa.data.household.repository.HouseholdMemberRepository;
import cloud.cholewa.data.household.repository.MemberDeviceRepository;
import cloud.cholewa.home.model.MemberPhoneDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Locale;

//a device belongs to one member and is addressed by its MAC address, unique across the whole registry
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberDeviceService {

    private static final String MAC_UNIQUE_CONSTRAINT = "member_devices_mac_uq";
    private static final String NAME_UNIQUE_CONSTRAINT = "member_devices_member_name_uq";

    private final HouseholdMemberRepository householdMemberRepository;
    private final MemberDeviceRepository memberDeviceRepository;
    private final MemberDeviceMapper memberDeviceMapper;

    public Mono<MemberPhoneDetails> addDevice(final String memberName, final MemberPhoneDetails device) {
        return findMember(memberName)
            .map(member -> memberDeviceMapper.toEntity(device, member.getId()))
            .flatMap(memberDeviceRepository::save)
            .onErrorMap(DuplicateKeyException.class, e -> duplicateDevice(e, memberName, device))
            .map(memberDeviceMapper::toMemberPhoneDetails);
    }

    public Mono<Void> removeDevice(final String memberName, final String mac) {
        return findDevice(memberName, mac)
            .flatMap(memberDeviceRepository::delete);
    }

    public Mono<MemberPhoneDetails> updateDevice(final String memberName, final String mac, final MemberPhoneDetails device) {
        return findDevice(memberName, mac)
            //the found row's id is what makes save() an UPDATE - a fresh entity would be INSERTed
            .map(existing -> memberDeviceMapper.toUpdatedEntity(existing, device))
            .flatMap(memberDeviceRepository::save)
            .onErrorMap(DuplicateKeyException.class, e -> duplicateDevice(e, memberName, device))
            .map(memberDeviceMapper::toMemberPhoneDetails);
    }

    private Mono<HouseholdMemberEntity> findMember(final String name) {
        return householdMemberRepository.findByNameIgnoreCase(name)
            .switchIfEmpty(Mono.error(HouseholdMemberNotFoundException.forName(name)));
    }

    //MACs are stored lowercase (the SDK pattern and a CHECK constraint), so a query parameter written
    //the way some tools print it, in uppercase, still finds the device
    private Mono<MemberDeviceEntity> findDevice(final String memberName, final String mac) {
        final String normalizedMac = mac.toLowerCase(Locale.ROOT);

        return findMember(memberName)
            .flatMap(member -> memberDeviceRepository.findByMemberIdAndMac(member.getId(), normalizedMac))
            .switchIfEmpty(Mono.error(new MemberDeviceNotFoundException(
                "Household member [" + memberName + "] has no device with MAC [" + normalizedMac + "]")));
    }

    private static Throwable duplicateDevice(
        final DuplicateKeyException exception,
        final String memberName,
        final MemberPhoneDetails device
    ) {
        final String message = String.valueOf(exception.getMessage());

        if (message.contains(MAC_UNIQUE_CONSTRAINT)) {
            return new MemberDeviceException("Device with MAC [" + device.getMac() + "] is already registered");
        }
        if (message.contains(NAME_UNIQUE_CONSTRAINT)) {
            return new MemberDeviceException(
                "Household member [" + memberName + "] already has a device named [" + device.getName() + "]");
        }
        return exception;
    }
}
