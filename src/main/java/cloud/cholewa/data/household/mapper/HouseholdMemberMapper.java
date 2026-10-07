package cloud.cholewa.data.household.mapper;

import cloud.cholewa.data.household.model.HouseholdMemberEntity;
import cloud.cholewa.home.model.HouseholdMember;
import cloud.cholewa.home.model.RoomName;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface HouseholdMemberMapper {

    @Mapping(target = "devices", ignore = true)
    HouseholdMember toHouseholdMember(HouseholdMemberEntity entity);

    //devices are managed through their own endpoints, never through the member payload.
    //A member added without a role is a resident: the SDK model has no default on purpose (a missing
    //role means "not sent"), and the default of the column never applies - every column is written
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()))")
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "role", source = "role", defaultValue = "RESIDENT")
    @Mapping(target = "rooms", source = "rooms", defaultExpression = "java(java.util.List.of())")
    HouseholdMemberEntity toEntity(HouseholdMember householdMember);

    //keeps the identity, creation time, activity and rooms of the stored row; name and phone change,
    //and the role only when the update names one - an update of the phone alone must not turn an
    //admin into a resident. The rooms have an operation of their own, like the activity: the model
    //cannot tell "no rooms sent" from "no rooms", so honouring them here would clear them
    @Mapping(target = "id", source = "existing.id")
    @Mapping(target = "createdAt", source = "existing.createdAt")
    @Mapping(target = "active", source = "existing.active")
    @Mapping(target = "rooms", source = "existing.rooms")
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
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()))")
    HouseholdMemberEntity withRooms(HouseholdMemberEntity existing, List<RoomName> rooms);
}
