package cloud.cholewa.data.household.service;

import cloud.cholewa.data.error.HouseholdException;
import cloud.cholewa.data.error.HouseholdMemberNotFoundException;
import cloud.cholewa.data.error.InvalidHouseholdMemberException;
import cloud.cholewa.data.household.mapper.HouseholdMemberMapper;
import cloud.cholewa.data.household.mapper.MemberDeviceMapper;
import cloud.cholewa.data.household.model.HouseholdMemberEntity;
import cloud.cholewa.data.household.model.MemberDeviceEntity;
import cloud.cholewa.data.household.repository.HouseholdMemberRepository;
import cloud.cholewa.data.household.repository.MemberDeviceRepository;
import cloud.cholewa.home.model.HouseholdMember;
import cloud.cholewa.home.model.HouseholdProfile;
import cloud.cholewa.home.model.MemberPhoneDetails;
import cloud.cholewa.home.model.RoomName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class HouseholdMemberService {

    //a unique index on upper(name) since V10 - the violation names it like a constraint
    private static final String NAME_UNIQUE_CONSTRAINT = "household_members_name_upper_uq";
    private static final String PHONE_UNIQUE_CONSTRAINT = "household_members_phone_uq";

    private final HouseholdMemberRepository householdMemberRepository;
    private final HouseholdMemberMapper householdMemberMapper;
    private final MemberDeviceRepository memberDeviceRepository;
    private final MemberDeviceMapper memberDeviceMapper;

    //two queries for the whole registry (about ten members) instead of one per member
    public Mono<List<HouseholdMember>> getAllHouseholdMembers() {
        return Mono.zip(
                householdMemberRepository.findAll().collectList(),
                memberDeviceRepository.findAll().collectMultimap(MemberDeviceEntity::getMemberId)
            )
            //an empty registry is an empty list, not an error: presence-service polls it
            .map(registry -> registry.getT1().stream()
                .map(member -> withDevices(member, registry.getT2().getOrDefault(member.getId(), List.of())))
                //names are case-insensitive, so is their order: "anna" before "Zofia"
                .sorted(Comparator.comparing(HouseholdMember::getName, String.CASE_INSENSITIVE_ORDER))
                .toList());
    }

    //the read of the web dashboard: who may use it, and as whom. Active members only - a member who is
    //switched off has no profile - and one query: the devices are neither read nor sent, and the phone
    //of a row stops at the mapper, whose target has no place for it. Ordered like the registry
    public Mono<List<HouseholdProfile>> getHouseholdProfiles() {
        return householdMemberRepository.findAllByActiveTrue()
            .map(householdMemberMapper::toHouseholdProfile)
            .sort(Comparator.comparing(HouseholdProfile::getName, String.CASE_INSENSITIVE_ORDER))
            .collectList();
    }

    public Mono<HouseholdMember> addHouseholdMember(final HouseholdMember householdMember) {
        //the rooms first: a refused list runs no query. What is stored is the checked list, the same
        //one a replacement of the rooms stores - not a second reading of the payload
        return Mono.fromCallable(() -> checkedRooms(householdMember.getRooms()))
            .flatMap(rooms -> householdMemberRepository.existsByNameIgnoreCase(householdMember.getName())
                .filter(Boolean::booleanValue)
                .flatMap(exists ->
                    Mono.<HouseholdMember>error(nameTaken(householdMember.getName())))
                .switchIfEmpty(Mono.defer(() ->
                    Mono.just(householdMemberMapper.toEntity(householdMember, rooms))
                        .flatMap(householdMemberRepository::save)
                        .onErrorMap(DuplicateKeyException.class, e -> duplicateMember(e, householdMember))
                        //a new member has no devices yet - the ones in the payload are ignored
                        .map(saved -> withDevices(saved, List.of())))));
    }

    public Mono<Void> removeHouseholdMember(final String name) {
        return findMember(name)
            .flatMap(householdMemberRepository::delete);
    }

    public Mono<HouseholdMember> updateHouseholdMember(final String name, final HouseholdMember householdMember) {
        return findMember(name)
            //the found row's id is what makes save() an UPDATE - a fresh entity would be INSERTed
            .flatMap(existing ->
                householdMemberRepository.save(householdMemberMapper.toUpdatedEntity(existing, householdMember)))
            .onErrorMap(DuplicateKeyException.class, e -> duplicateMember(e, householdMember))
            .flatMap(this::withStoredDevices);
    }

    //an operation of its own rather than a field of the update, for the reason the activity is one: the
    //SDK model starts with an empty list, so an update that left the rooms out would clear them. The
    //list replaces the stored one, in the order given; an empty list leaves the member without rooms
    public Mono<HouseholdMember> replaceRooms(final String name, final List<RoomName> rooms) {
        return Mono.fromCallable(() -> checkedRooms(rooms))
            .flatMap(checked -> findMember(name)
                .map(existing -> householdMemberMapper.withRooms(existing, checked)))
            .flatMap(householdMemberRepository::save)
            .flatMap(this::withStoredDevices);
    }

    public Mono<HouseholdMember> activateHouseholdMember(final String name) {
        return changeActivity(name, true);
    }

    public Mono<HouseholdMember> deactivateHouseholdMember(final String name) {
        return changeActivity(name, false);
    }

    //separate operations rather than a field of the update: active defaults to true in the SDK model,
    //so an update that simply omitted it would reactivate the member
    private Mono<HouseholdMember> changeActivity(final String name, final boolean active) {
        return findMember(name)
            .map(existing -> householdMemberMapper.withActive(existing, active))
            .flatMap(householdMemberRepository::save)
            .flatMap(this::withStoredDevices);
    }

    private HouseholdMember withDevices(final HouseholdMemberEntity member, final Collection<MemberDeviceEntity> devices) {
        final HouseholdMember householdMember = householdMemberMapper.toHouseholdMember(member);
        householdMember.setDevices(devices.stream()
            .map(memberDeviceMapper::toMemberPhoneDetails)
            .sorted(Comparator.comparing(MemberPhoneDetails::getName))
            .toList());
        return householdMember;
    }

    //responses of member operations carry the member's devices, like GET /household does - a client
    //replacing its cached member with the response must not lose them
    private Mono<HouseholdMember> withStoredDevices(final HouseholdMemberEntity member) {
        return memberDeviceRepository.findAllByMemberId(member.getId())
            .collectList()
            .map(devices -> withDevices(member, devices));
    }

    //what the SDK model does not check: it is a plain list, so that the order survives. A room is a
    //room of the member once, and a null among them is nobody's room. Reported by the value a client
    //sends ("living room"), not by the name of the constant
    private static List<RoomName> checkedRooms(final List<RoomName> rooms) {
        //a member built with the Lombok builder has no list at all - the builder skips the model's default
        if (rooms == null) {
            return List.of();
        }
        final Set<RoomName> seen = EnumSet.noneOf(RoomName.class);
        for (final RoomName room : rooms) {
            if (room == null) {
                throw new InvalidHouseholdMemberException("A room must not be null");
            }
            if (!seen.add(room)) {
                throw new InvalidHouseholdMemberException("Room [" + room.getValue() + "] is listed more than once");
            }
        }
        return List.copyOf(rooms);
    }

    private static HouseholdException nameTaken(final String name) {
        return new HouseholdException("Household member named [" + name + "] already exists");
    }

    private Mono<HouseholdMemberEntity> findMember(final String name) {
        return householdMemberRepository.findByNameIgnoreCase(name)
            .switchIfEmpty(Mono.error(HouseholdMemberNotFoundException.forName(name)));
    }

    //no service-wide mapping of DuplicateKeyException exists (the cholewa-commons default would answer a
    //bare "Duplicate Key") - the violated constraint tells which household field clashed. A rename to
    //another member's name in any letter case hits the upper(name) index (V10); changing only the case of
    //the member's own name updates the same row and cannot clash
    private static Throwable duplicateMember(final DuplicateKeyException exception, final HouseholdMember householdMember) {
        final String message = String.valueOf(exception.getMessage());

        if (message.contains(PHONE_UNIQUE_CONSTRAINT)) {
            return new HouseholdException(
                "Phone number [" + householdMember.getPhone() + "] is already assigned to another household member");
        }
        if (message.contains(NAME_UNIQUE_CONSTRAINT)) {
            return nameTaken(householdMember.getName());
        }
        return exception;
    }
}
