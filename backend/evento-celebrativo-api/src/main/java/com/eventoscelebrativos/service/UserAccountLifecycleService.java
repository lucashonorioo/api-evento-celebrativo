package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.AdminPasswordResetRequestDTO;
import com.eventoscelebrativos.dto.request.PersonActiveRequestDTO;
import com.eventoscelebrativos.dto.request.SelfPasswordChangeRequestDTO;
import com.eventoscelebrativos.dto.request.UserAccountCreateRequestDTO;
import com.eventoscelebrativos.dto.request.UserAccountEnabledRequestDTO;
import com.eventoscelebrativos.dto.response.UserAccountLifecycleResponseDTO;
import com.eventoscelebrativos.model.Person;
import com.eventoscelebrativos.model.Role;

public interface UserAccountLifecycleService {

    UserAccountLifecycleResponseDTO findAccountState(Long personId);

    UserAccountLifecycleResponseDTO createAccount(Long personId, UserAccountCreateRequestDTO request);

    void updateAccountEnabled(Long personId, UserAccountEnabledRequestDTO request);

    void updatePersonActive(Long personId, PersonActiveRequestDTO request);

    void resetPassword(Long personId, AdminPasswordResetRequestDTO request);

    void changeOwnPassword(SelfPasswordChangeRequestDTO request);

    Person updateRole(Long personId, String requestedRole);

    Role requireRole(String roleName);
}
