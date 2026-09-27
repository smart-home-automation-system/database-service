package cloud.cholewa.data.household.api;

import cloud.cholewa.data.household.api.model.HouseholdRequest;
import cloud.cholewa.data.household.api.model.HouseholdResponse;
import cloud.cholewa.data.household.service.HouseholdMemberService;
import cloud.cholewa.home.model.HouseholdMember;
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
    Mono<ResponseEntity<HouseholdResponse>> addHouseholdMember(
        @Valid @RequestBody final HouseholdRequest householdRequest
    ) {
        return householdService.addHouseholdMember(householdRequest)
            .map(householdMember -> ResponseEntity.status(HttpStatus.CREATED).body(householdMember));
    }

    @DeleteMapping("/member")
    Mono<ResponseEntity<Void>> removeHouseholdMember(@RequestParam final String name) {
        return householdService.removeHouseholdMember(name)
            .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @PatchMapping("/member")
    Mono<ResponseEntity<HouseholdMember>> updateHouseholdMember(
        @RequestParam final String name,
        @Valid @RequestBody final HouseholdRequest householdRequest
    ) {
        return householdService.updateHouseholdMember(name, householdRequest)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/member/{memberId}/device")
    Mono<Void> addHouseholdDevice(@PathVariable String memberId) {
        return Mono.empty();
    }

    @DeleteMapping("/member/{memberId}/device")
    Mono<Void> removeHouseholdDevice(@PathVariable String memberId) {
        return Mono.empty();
    }

    @PatchMapping("/member/{memberId}/device")
    Mono<Void> updateHouseholdDevices(@PathVariable String memberId) {
        return Mono.empty();
    }
}
