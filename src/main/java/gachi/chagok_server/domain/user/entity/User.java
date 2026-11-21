package gachi.chagok_server.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.*;

@Entity @Getter @Table(name="users", uniqueConstraints=@UniqueConstraint(columnNames="phone_number"))
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class User {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="phone_number",length=20,unique=true) private String phoneNumber;
    @Column(length=100) private String passwordHash;
    @Column(nullable=false,length=100) private String name;
    private LocalDate birthDate;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private LoginType loginType = LoginType.PHONE;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private FontSize fontSize = FontSize.NORMAL;
    private Instant fontSizeUpdatedAt;
    @Column(nullable=false) private boolean onboardingCompleted;
    @Column(nullable=false,updatable=false) private Instant createdAt = Instant.now();
    @Column(nullable=false) private boolean deleted = false;
    private Instant deletedAt;
    private User(String phone, String password, String name) { this.phoneNumber=phone;this.passwordHash=password;this.name=name; }
    public static User phoneUser(String phone,String password,String name) { return new User(phone,password,name); }
    public static User socialUser(String name) { return new User(null,null,name); }
    public void setSocialProvider(LoginType provider) { if(provider==LoginType.PHONE) throw new IllegalArgumentException();this.loginType=provider; }
    public void setBirthDateOnce(LocalDate value) { if(birthDate!=null && !birthDate.equals(value)) throw new IllegalArgumentException("BIRTH_DATE_LOCKED"); if(birthDate==null) birthDate=value; }
    public void changeFontSize(FontSize value) { fontSize=value;fontSizeUpdatedAt=Instant.now(); }
    public void markOnboardingCompleted() { onboardingCompleted=true; }
    public void changePasswordHash(String next) { this.passwordHash=next; }
    public void deactivate() { deleted=true;deletedAt=Instant.now(); }
}
