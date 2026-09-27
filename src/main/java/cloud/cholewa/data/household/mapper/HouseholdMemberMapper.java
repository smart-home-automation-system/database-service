package cloud.cholewa.data.household.mapper;

import cloud.cholewa.data.household.api.model.HouseholdRequest;
import cloud.cholewa.data.household.api.model.HouseholdResponse;
import cloud.cholewa.data.household.model.HouseholdMemberEntity;
import cloud.cholewa.home.model.HouseholdMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface HouseholdMemberMapper {
    
    @Mapping(target = "devices",ignore = true)
    HouseholdMember toHouseholdMember(HouseholdMemberEntity entity);
    
    HouseholdResponse toHouseholdResponse(HouseholdMemberEntity entity);
    
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now(java.time.ZoneOffset.systemDefault()))")
    @Mapping(target = "updatedAt", ignore = true)
    HouseholdMemberEntity toEntity(HouseholdRequest householdRequest);
}
