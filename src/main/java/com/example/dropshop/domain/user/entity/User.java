package com.example.dropshop.domain.user.entity;

import com.example.dropshop.common.entity.BaseEntity;
import com.example.dropshop.domain.user.enums.UserRole;
import com.example.dropshop.domain.user.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)

    private String password;

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    private LocalDateTime deletedAt;

    /**
     * 빌더의 접근 레벨을 PRIVATE으로 설정하여 외부에서 직접 호출을 막습니다.
     */
    @Builder(access = AccessLevel.PRIVATE)
    private User(String email, String password, String nickname, UserRole role) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.status = UserStatus.ACTIVE;
        this.role = (role != null) ? role : UserRole.USER;
    }

    /**
     * 정적 팩토리 메서드: 회원가입 시 필요한 최소한의 정보로 객체를 생성합니다.
     */
    public static User signup(String email, String password, String nickname) {
        return User.builder()
                .email(email)
                .password(password)
                .nickname(nickname)
                .role(UserRole.USER)
                .build();
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void promoteToSeller() {
        this.role = UserRole.SELLER;
    }

    public void withdraw() {
        if (this.status == UserStatus.DELETED) {
            throw new IllegalStateException("이미 탈퇴된 사용자입니다.");
        }
        this.status = UserStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }
}
