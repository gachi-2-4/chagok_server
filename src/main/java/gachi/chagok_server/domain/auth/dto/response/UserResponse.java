package gachi.chagok_server.domain.auth.dto.response;
import gachi.chagok_server.domain.user.entity.User;
public record UserResponse(Long id, String name, String phoneNumber) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getPhoneNumber());
    }
}
