package com.eventoscelebrativos.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tb_user_account_role")
@IdClass(UserAccountRoleId.class)
public class UserAccountRole {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    protected UserAccountRole() {
    }

    public UserAccountRole(UserAccount userAccount, Role role) {
        this.userAccount = userAccount;
        this.role = role;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public Role getRole() {
        return role;
    }
}
