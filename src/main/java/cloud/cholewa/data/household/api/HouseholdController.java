package cloud.cholewa.data.household.api;

import cloud.cholewa.data.household.service.HouseholdMemberService;
import cloud.cholewa.data.household.service.MemberDeviceService;
import cloud.cholewa.home.model.HouseholdMember;
import cloud.cholewa.home.model.MemberPhoneDetails;
import cloud.cholewa.home.model.RoomName;
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
import org.springframework.web.bind.annotation.PutMapping;
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
    private final MemberDeviceService memberDeviceService;

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

    //the whole list of the member's rooms, in the order they are shown; [] leaves the member without
    //rooms. PUT, not PATCH: the list is replaced, never merged
    @PutMapping("/member/{name}/rooms")
    Mono<ResponseEntity<HouseholdMember>> replaceHouseholdMemberRooms(
        @PathVariable final String name,
        @RequestBody final List<RoomName> rooms
    ) {
        return householdService.replaceRooms(name, rooms)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/member/{name}/activate")
    Mono<ResponseEntity<HouseholdMember>> activateHouseholdMember(@PathVariable final String name) {
        return householdService.activateHouseholdMember(name)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/member/{name}/deactivate")
    Mono<ResponseEntity<HouseholdMember>> deactivateHouseholdMember(@PathVariable final String name) {
        return householdService.deactivateHouseholdMember(name)
            .map(ResponseEntity::ok);
    }

    @PostMapping("/member/{name}/device")
    Mono<ResponseEntity<MemberPhoneDetails>> addHouseholdDevice(
        @PathVariable final String name,
        @Valid @RequestBody final MemberPhoneDetails device
    ) {
        return memberDeviceService.addDevice(name, device)
            .map(added -> ResponseEntity.status(HttpStatus.CREATED).body(added));
    }

    @DeleteMapping("/member/{name}/device")
    Mono<ResponseEntity<Void>> removeHouseholdDevice(@PathVariable final String name, @RequestParam final String mac) {
        return memberDeviceService.removeDevice(name, mac)
            .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @PatchMapping("/member/{name}/device")
    Mono<ResponseEntity<MemberPhoneDetails>> updateHouseholdDevice(
        @PathVariable final String name,
        @RequestParam final String mac,
        @Valid @RequestBody final MemberPhoneDetails device
    ) {
        return memberDeviceService.updateDevice(name, mac, device)
            .map(ResponseEntity::ok);
    }
}
