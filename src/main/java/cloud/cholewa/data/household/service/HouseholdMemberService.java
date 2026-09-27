package cloud.cholewa.data.household.service;

import cloud.cholewa.data.error.HouseholdException;
import cloud.cholewa.data.error.HouseholdMemberNotFoundException;
import cloud.cholewa.data.error.HouseholdNotFoundException;
import cloud.cholewa.data.household.mapper.HouseholdMemberMapper;
import cloud.cholewa.data.household.repository.HouseholdMemberRepository;
import cloud.cholewa.home.model.HouseholdMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HouseholdMemberService {

    private static final String NAME_UNIQUE_CONSTRAINT = "household_members_name_uq";
    private static final String PHONE_UNIQUE_CONSTRAINT = "household_members_phone_uq";

    private final HouseholdMemberRepository householdMemberRepository;
    private final HouseholdMemberMapper householdMemberMapper;

    public Mono<List<HouseholdMember>> getAllHouseholdMembers() {
        return householdMemberRepository.findAll()
            .map(householdMemberMapper::toHouseholdMember)
            //HouseholdMember is not Comparable - the no-arg collectSortedList() throws once there are two members
            .collectSortedList(Comparator.comparing(HouseholdMember::getName))
            .filter(list -> !list.isEmpty())
            .switchIfEmpty(Mono.error(new HouseholdNotFoundException("Please update household members, adding new one")));
    }

    public Mono<HouseholdMember> addHouseholdMember(final HouseholdMember householdMember) {
        return householdMemberRepository.existsByNameIgnoreCase(householdMember.getName())
            .filter(Boolean::booleanValue)
            .flatMap(exists ->
                Mono.<HouseholdMember>error(new HouseholdException("Household member already exists")))
            .switchIfEmpty(Mono.defer(() ->
                Mono.just(householdMemberMapper.toEntity(householdMember))
                    .flatMap(householdMemberRepository::save)
                    .onErrorMap(DuplicateKeyException.class, e -> duplicateMember(e, householdMember))
                    .map(householdMemberMapper::toHouseholdMember)));
    }

    public Mono<Void> removeHouseholdMember(final String name) {
        return householdMemberRepository.findByNameIgnoreCase(name)
            .switchIfEmpty(Mono.error(new HouseholdMemberNotFoundException("No household member named [" + name + "]")))
            .flatMap(householdMemberRepository::delete);
    }

    public Mono<HouseholdMember> updateHouseholdMember(final String name, final HouseholdMember householdMember) {
        return householdMemberRepository.findByNameIgnoreCase(name)
            .switchIfEmpty(Mono.error(new HouseholdMemberNotFoundException("No household member named [" + name + "]")))
            //the found row's id is what makes save() an UPDATE - a fresh entity would be INSERTed
            .flatMap(existing ->
                householdMemberRepository.save(householdMemberMapper.toUpdatedEntity(existing, householdMember)))
            .onErrorMap(DuplicateKeyException.class, e -> duplicateMember(e, householdMember))
            .map(householdMemberMapper::toHouseholdMember);
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
        return householdMemberRepository.findByNameIgnoreCase(name)
            .switchIfEmpty(Mono.error(new HouseholdMemberNotFoundException("No household member named [" + name + "]")))
            .map(existing -> householdMemberMapper.withActive(existing, active))
            .flatMap(householdMemberRepository::save)
            .map(householdMemberMapper::toHouseholdMember);
    }

    //DuplicateKeyException is registered globally for the Eaton configuration, whose message would be
    //misleading here - the violated constraint tells which household field clashed
    private static Throwable duplicateMember(final DuplicateKeyException exception, final HouseholdMember householdMember) {
        final String message = String.valueOf(exception.getMessage());

        if (message.contains(PHONE_UNIQUE_CONSTRAINT)) {
            return new HouseholdException(
                "Phone number [" + householdMember.getPhone() + "] is already assigned to another household member");
        }
        if (message.contains(NAME_UNIQUE_CONSTRAINT)) {
            return new HouseholdException("Household member named [" + householdMember.getName() + "] already exists");
        }
        return exception;
    }
}
