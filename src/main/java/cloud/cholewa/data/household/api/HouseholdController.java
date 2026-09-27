package cloud.cholewa.data.household.api;

import cloud.cholewa.data.household.service.HouseholdMemberService;
import cloud.cholewa.home.model.HouseholdMember;
import cloud.cholewa.home.model.MemberPhoneDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

//members are addressed by name and their devices by MAC address - both are unique in the database,
//so the API needs no surrogate id that a client would first have to look up
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/household")
public class HouseholdController {

    private final HouseholdMemberService householdService;

    @GetMapping
    Mono<ResponseEntity<List<HouseholdMember>>> getAllHouseholdMembers() {
        return householdService.getAllHouseholdMembers()
            .map(ResponseEntity::ok);
    }

    @PostMapping("/member")
    Mono<ResponseEntity<HouseholdMember>> addHouseholdMember(
        @Valid @RequestBody final HouseholdMember householdMember
    ) {
        return householdService.addHouseholdMember(householdMember)
            .map(added -> ResponseEntity.status(HttpStatus.CREATED).body(added));
    }

    @DeleteMapping("/member/{name}")
    Mono<ResponseEntity<Void>> removeHouseholdMember(@PathVariable final String name) {
        return householdService.removeHouseholdMember(name)
            .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @PatchMapping("/member/{name}")
    Mono<ResponseEntity<HouseholdMember>> updateHouseholdMember(
        @PathVariable final String name,
        @Valid @RequestBody final HouseholdMember householdMember
    ) {
        return householdService.updateHouseholdMember(name, householdMember)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/member/{name}/device")
    Mono<Void> addHouseholdDevice(
        @PathVariable final String name,
        @Valid @RequestBody final MemberPhoneDetails device
    ) {
        return Mono.empty();
    }

    @DeleteMapping("/member/{name}/device")
    Mono<Void> removeHouseholdDevice(@PathVariable final String name, @RequestParam final String mac) {
        return Mono.empty();
    }

    @PatchMapping("/member/{name}/device")
    Mono<Void> updateHouseholdDevice(
        @PathVariable final String name,
        @RequestParam final String mac,
        @Valid @RequestBody final MemberPhoneDetails device
    ) {
        return Mono.empty();
    }
}
