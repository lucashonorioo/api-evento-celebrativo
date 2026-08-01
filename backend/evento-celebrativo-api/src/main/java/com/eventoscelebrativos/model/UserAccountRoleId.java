package com.eventoscelebrativos.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class UserAccountRoleId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userAccount;
    private Long role;

    public UserAccountRoleId() {
    }

    public UserAccountRoleId(Long userAccount, Long role) {
        this.userAccount = userAccount;
        this.role = role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserAccountRoleId that = (UserAccountRoleId) o;
        return Objects.equals(userAccount, that.userAccount) && Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userAccount, role);
    }
}
