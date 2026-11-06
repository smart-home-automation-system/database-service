package cloud.cholewa.data.household.mapper;

import cloud.cholewa.data.household.model.MemberRequestEntity;
import cloud.cholewa.home.model.MemberRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

//A household request (HAS-409) as the SDK model: the row names its member by id, the model by name - the
//name is given, as the registry spells it now. Nothing maps the other way: a new row is built by the
//service from the checked text, the author as the registry spells it and the time of the server, never
//from what else a body carries
@Mapper(componentModel = "spring")
public interface MemberRequestMapper {

    @Mapping(target = "member", source = "member")
    MemberRequest toMemberRequest(MemberRequestEntity entity, String member);
}
