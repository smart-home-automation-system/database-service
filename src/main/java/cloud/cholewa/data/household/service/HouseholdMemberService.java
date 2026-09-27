package cloud.cholewa.data.household.service;

import cloud.cholewa.data.error.HouseholdException;
import cloud.cholewa.data.error.HouseholdMemberNotFoundException;
import cloud.cholewa.data.error.HouseholdNotFoundException;
import cloud.cholewa.data.household.mapper.HouseholdMemberMapper;
import cloud.cholewa.data.household.repository.HouseholdMemberRepository;
import cloud.cholewa.home.model.HouseholdMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HouseholdMemberService {

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
            .map(householdMemberMapper::toHouseholdMember);
    }
}
