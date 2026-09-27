package cloud.cholewa.data.household.mapper;

import cloud.cholewa.data.household.model.MemberDeviceEntity;
import cloud.cholewa.home.model.MemberPhoneDetails;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MemberDeviceMapper {

    MemberPhoneDetails toMemberPhoneDetails(MemberDeviceEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()))")
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "memberId", source = "memberId")
    @Mapping(target = "name", source = "device.name")
    @Mapping(target = "mac", source = "device.mac")
    MemberDeviceEntity toEntity(MemberPhoneDetails device, Long memberId);

    //keeps the identity, owner and creation time of the stored row; only name and MAC change
    @Mapping(target = "id", source = "existing.id")
    @Mapping(target = "createdAt", source = "existing.createdAt")
    @Mapping(target = "memberId", source = "existing.memberId")
    @Mapping(target = "name", source = "device.name")
    @Mapping(target = "mac", source = "device.mac")
    @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()))")
    MemberDeviceEntity toUpdatedEntity(MemberDeviceEntity existing, MemberPhoneDetails device);
}
