package cloud.cholewa.data.household.service;

import cloud.cholewa.data.error.HouseholdException;
import cloud.cholewa.data.error.HouseholdMemberNotFoundException;
import cloud.cholewa.data.error.HouseholdNotFoundException;
import cloud.cholewa.data.household.api.model.HouseholdRequest;
import cloud.cholewa.data.household.api.model.HouseholdResponse;
import cloud.cholewa.data.household.mapper.HouseholdMemberMapper;
import cloud.cholewa.data.household.repository.HouseholdMemberRepository;
import cloud.cholewa.home.model.HouseholdMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

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
            .collectSortedList()
            .filter(list -> !list.isEmpty())
            .switchIfEmpty(Mono.error(new HouseholdNotFoundException("Please update household members, adding new one")));
    }

    public Mono<HouseholdResponse> addHouseholdMember(final HouseholdRequest householdRequest) {
        return householdMemberRepository.existsByNameIgnoreCase(householdRequest.name())
            .filter(Boolean::booleanValue)
            .flatMap(exists ->
                Mono.<HouseholdResponse>error(new HouseholdException("Household member already exists")))
            .switchIfEmpty(Mono.defer(() ->
                Mono.just(householdMemberMapper.toEntity(householdRequest))
                    .flatMap(householdMemberRepository::save)
                    .map(householdMemberMapper::toHouseholdResponse)));
    }

    public Mono<Void> removeHouseholdMember(final String name) {
        return householdMemberRepository.findByNameIgnoreCase(name)
            .switchIfEmpty(Mono.error(new HouseholdMemberNotFoundException("No household member named [" + name + "]")))
            .flatMap(householdMemberRepository::delete);
    }

    public Mono<HouseholdMember> updateHouseholdMember(final String name, final HouseholdRequest householdRequest) {
        return householdMemberRepository.findByNameIgnoreCase(name)
            .switchIfEmpty(Mono.error(new HouseholdMemberNotFoundException("No household member named [" + name + "]")))
            .flatMap(householdMember ->
                householdMemberRepository.save(householdMemberMapper.toEntity(householdRequest)))
            .map(householdMemberMapper::toHouseholdMember);
    }
}
