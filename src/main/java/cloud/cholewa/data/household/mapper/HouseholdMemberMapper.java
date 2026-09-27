package cloud.cholewa.data.household.mapper;

import cloud.cholewa.data.household.model.HouseholdMemberEntity;
import cloud.cholewa.home.model.HouseholdMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface HouseholdMemberMapper {
    
    @Mapping(target = "devices",ignore = true)
    HouseholdMember toHouseholdMember(HouseholdMemberEntity entity);
    
    //devices are managed through their own endpoints, never through the member payload
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now(java.time.ZoneOffset.systemDefault()))")
    @Mapping(target = "updatedAt", ignore = true)
    HouseholdMemberEntity toEntity(HouseholdMember householdMember);

    //keeps the identity, creation time and activity of the stored row; only name and phone change
    @Mapping(target = "id", source = "existing.id")
    @Mapping(target = "createdAt", source = "existing.createdAt")
    @Mapping(target = "active", source = "existing.active")
    @Mapping(target = "name", source = "householdMember.name")
    @Mapping(target = "phone", source = "householdMember.phone")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()))")
    HouseholdMemberEntity toUpdatedEntity(HouseholdMemberEntity existing, HouseholdMember householdMember);

    @Mapping(target = "id", source = "existing.id")
    @Mapping(target = "createdAt", source = "existing.createdAt")
    @Mapping(target = "name", source = "existing.name")
    @Mapping(target = "phone", source = "existing.phone")
    @Mapping(target = "active", source = "active")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()))")
    HouseholdMemberEntity withActive(HouseholdMemberEntity existing, boolean active);
}
