package com.hex.aicreator.model.vo.user;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class LoginUserVO implements Serializable {
    private Long id;
    private String userAccount;
    private String userName;
    private String userAvatar;
    private String userProfile;
    private String userRole;

    /**
     * 成为会员时间
     */
    private LocalDateTime vipTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
