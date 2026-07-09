window.openMyInfoModal = function() {
    if (!loggedInUserRawData) { showToast('사용자 정보 데이터가 비어있습니다.', true); return; }

    document.getElementById('m-me-id').value    = loggedInUserRawData.userId    || '';
    document.getElementById('m-me-name').value  = loggedInUserRawData.name      || '';
    document.getElementById('m-me-email').value = loggedInUserRawData.email     || '';
    document.getElementById('m-me-phone').value = loggedInUserRawData.phoneNumber || '';
    document.getElementById('m-me-birth').value = loggedInUserRawData.birthDate || '';
    document.getElementById('m-me-addr').value  = loggedInUserRawData.address   || '';
    document.getElementById('m-me-role').value = loggedInUserRawData.role || '';

    isPasswordVerified = false;
    toggleMyFieldsDisableState(true);

    document.getElementById('my-password-manage-area').innerHTML = `
      <label>비밀번호 계정 관리</label>
      <button class="d-btn d-btn-warning" style="font-size: 13.5px; padding: 11px;" onclick="openPasswordVerifyModal()">
        <i class="ti ti-lock-open"></i> 비밀번호 확인하기
      </button>
    `;

    const actionArea = document.getElementById('my-info-action-area');
    actionArea.className = '';
    actionArea.innerHTML = `<button class="d-btn d-btn-secondary" onclick="closeMyInfoModal()">닫기</button>`;

    document.getElementById('my-info-modal').style.display = 'flex';
  };

  window.closeMyInfoModal = function() {
    document.getElementById('my-info-modal').style.display = 'none';
  };

  function toggleMyFieldsDisableState(shouldDisable) {
    document.getElementById('m-me-id').disabled    = true;
    document.getElementById('m-me-name').disabled  = shouldDisable;
    document.getElementById('m-me-email').disabled = shouldDisable;
    document.getElementById('m-me-phone').disabled = shouldDisable;
    document.getElementById('m-me-birth').disabled = shouldDisable;
    document.getElementById('m-me-addr').disabled  = shouldDisable;
  }

  window.handleInitialModifyClick = function() {
    if (!isPasswordVerified) { showToast('비밀번호 확인 절차가 필요합니다.', true); return; }
    toggleMyFieldsDisableState(false);
    const actionArea = document.getElementById('my-info-action-area');
    actionArea.className = 'd-form-row';
    actionArea.innerHTML = `
      <button class="d-btn d-btn-secondary" onclick="openMyInfoModal()">취소</button>
      <button class="d-btn" onclick="updateMyProfileDetails()">변경사항 저장</button>
    `;
  };

  window.openPasswordVerifyModal = function() {
    const currentUserId = document.getElementById('m-me-id').value;
    if (!currentUserId) { showToast('유저 아이디를 식별할 수 없습니다.', true); return; }
    document.getElementById('m-verify-id').value = currentUserId;
    document.getElementById('m-verify-password').value = '';
    document.getElementById('my-password-modal').style.display = 'flex';
    document.getElementById('m-verify-password').focus();
  };

  window.closePasswordVerifyModal = function() {
    document.getElementById('my-password-modal').style.display = 'none';
  };

  window.handleVerifyKeydown = function(e) {
    if (e.key === 'Enter') { e.preventDefault(); submitPasswordVerification(); }
  };

  window.submitPasswordVerification = async function() {
    const userId   = document.getElementById('m-verify-id').value;
    const password = document.getElementById('m-verify-password').value;
    if (!password) { showToast('비밀번호를 입력해 주세요.', true); return; }

    try {
      const res = await window.Fetch(`${base()}/admin/api/${API.VERSION}/user/validate/info`, {
        method: 'POST', body: { userId, password }
      });

      if (res.ok) {
        showToast('비밀번호 인증에 성공했습니다.');
        closePasswordVerifyModal();
        isPasswordVerified = true;

        document.getElementById('my-password-manage-area').innerHTML = `
          <label>비밀번호 계정 관리</label>
          <div style="background: #e8f5e9; color: #2b8a3e; padding: 11px 14px; border-radius: 8px; font-size: 13.5px; font-weight: 600; display: flex; align-items: center; gap: 7px; border: 1px solid #c8e6c9;">
            <i class="ti ti-circle-check" style="font-size: 17px;"></i> 계정 소유자 인증 완료
          </div>
        `;

        const actionArea = document.getElementById('my-info-action-area');
        actionArea.className = '';
        actionArea.innerHTML = `
          <button class="d-btn" style="margin-bottom: 0.625rem;" onclick="handleInitialModifyClick()">정보 수정하기</button>
          <button class="d-btn d-btn-secondary" onclick="closeMyInfoModal()">닫기</button>
        `;
      } else {
        showToast('비밀번호가 일치하지 않습니다. 다시 시도해 주세요.', true);
      }
    } catch (e) {
      showToast('서버 인증 통신 장애 발생', true);
    }
  };

  window.updateMyProfileDetails = async function() {
    const body = {
      userId:      document.getElementById('m-me-id').value,
      name:        document.getElementById('m-me-name').value.trim(),
      email:       document.getElementById('m-me-email').value.trim(),
      phoneNumber: document.getElementById('m-me-phone').value.trim(),
      birthDate:   document.getElementById('m-me-birth').value || null,
      address:     document.getElementById('m-me-addr').value.trim(),
      role: document.getElementById('m-me-role').value
    };

    if (!body.name) { showToast('이름은 공백으로 둘 수 없습니다.', true); return; }

    try {
      const res = await window.Fetch(`${base()}/admin/api/${API.VERSION}/user/update/me`, { method: 'PUT', body });
      if (res.ok) {
        showToast('내 정보가 성공적으로 업데이트되었습니다.');
        await loadMyProfileHeader();
        closeMyInfoModal();
      } else {
        showToast('정보 업데이트 수정 처리에 실패했습니다.', true);
      }
    } catch (e) {
      showToast('통신 오류가 발생했습니다.', true);
    }
  };

  function logout() {
    localStorage.clear();
    document.cookie = "accessToken=; path=/; max-age=0;";
    window.showToast('로그아웃 되었습니다.');
    setTimeout(() => { location.href = `${base()}/admin/api/${API.VERSION}/view/login`; }, 800);
  }
