package cloud.cholewa.data.household.mapper;

import cloud.cholewa.data.household.model.HouseholdMemberEntity;
import cloud.cholewa.home.model.HouseholdMember;
import cloud.cholewa.home.model.HouseholdProfile;
import cloud.cholewa.home.model.MemberPermission;
import cloud.cholewa.home.model.RoomName;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface HouseholdMemberMapper {

    @Mapping(target = "devices", ignore = true)
    HouseholdMember toHouseholdMember(HouseholdMemberEntity entity);

    //what the web dashboard may know about a member. The target has no phone and no devices, and that
    //is the whole point of it: a field is not left out here, it does not exist in the model
    HouseholdProfile toHouseholdProfile(HouseholdMemberEntity entity);

    //devices are managed through their own endpoints, never through the member payload.
    //THE place where a missing role becomes a resident: the SDK model has no default on purpose (a
    //missing role means "not sent"), and the default of the column never applies - every column is
    //written. The rooms and the permissions are the lists the service has checked, not the ones in the
    //payload
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()))")
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "name", source = "householdMember.name")
    @Mapping(target = "phone", source = "householdMember.phone")
    @Mapping(target = "role", source = "householdMember.role", defaultValue = "RESIDENT")
    @Mapping(target = "rooms", source = "rooms")
    @Mapping(target = "permissions", source = "permissions")
    HouseholdMemberEntity toEntity(
        HouseholdMember householdMember,
        List<RoomName> rooms,
        List<MemberPermission> permissions
    );

    //keeps the identity, creation time, activity and rooms of the stored row; name and phone change,
    //and the role only when the update names one (THE place where a missing role means "keep it") - an update of the phone alone must not turn an
    //admin into a resident. The rooms have an operation of their own, like the activity: the model
    //cannot tell "no rooms sent" from "no rooms", so honouring them here would clear them. The same
    //holds for the permissions, which would be taken away by an update of a phone number
    @Mapping(target = "id", source = "existing.id")
    @Mapping(target = "createdAt", source = "existing.createdAt")
    @Mapping(target = "active", source = "existing.active")
    @Mapping(target = "rooms", source = "existing.rooms")
    @Mapping(target = "permissions", source = "existing.permissions")
    @Mapping(target = "name", source = "householdMember.name")
    @Mapping(target = "phone", source = "householdMember.phone")
    @Mapping(
        target = "role",
        expression = "java(householdMember.getRole() != null ? householdMember.getRole() : existing.getRole())"
    )
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()))")
    HouseholdMemberEntity toUpdatedEntity(HouseholdMemberEntity existing, HouseholdMember householdMember);

    @Mapping(target = "id", source = "existing.id")
    @Mapping(target = "createdAt", source = "existing.createdAt")
    @Mapping(target = "name", source = "existing.name")
    @Mapping(target = "phone", source = "existing.phone")
    @Mapping(target = "role", source = "existing.role")
    @Mapping(target = "rooms", source = "existing.rooms")
    @Mapping(target = "permissions", source = "existing.permissions")
    @Mapping(target = "active", source = "active")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()))")
    HouseholdMemberEntity withActive(HouseholdMemberEntity existing, boolean active);

    @Mapping(target = "id", source = "existing.id")
    @Mapping(target = "createdAt", source = "existing.createdAt")
    @Mapping(target = "name", source = "existing.name")
    @Mapping(target = "phone", source = "existing.phone")
    @Mapping(target = "active", source = "existing.active")
    @Mapping(target = "role", source = "existing.role")
    @Mapping(target = "rooms", source = "rooms")
    @Mapping(target = "permissions", source = "existing.permissions")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()))")
    HouseholdMemberEntity withRooms(HouseholdMemberEntity existing, List<RoomName> rooms);

    @Mapping(target = "id", source = "existing.id")
    @Mapping(target = "createdAt", source = "existing.createdAt")
    @Mapping(target = "name", source = "existing.name")
    @Mapping(target = "phone", source = "existing.phone")
    @Mapping(target = "active", source = "existing.active")
    @Mapping(target = "role", source = "existing.role")
    @Mapping(target = "rooms", source = "existing.rooms")
    @Mapping(target = "permissions", source = "permissions")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()))")
    HouseholdMemberEntity withPermissions(HouseholdMemberEntity existing, List<MemberPermission> permissions);
}
