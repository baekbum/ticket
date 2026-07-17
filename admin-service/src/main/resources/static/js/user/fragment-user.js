(function () {
    const API = { VERSION: 'v1', LOCAL_PORT: '8999', DEV_PORT: '8080' };
    const BASE_URL = window.location.port === API.LOCAL_PORT ? `http://localhost:${API.LOCAL_PORT}/admin` : '';
    const USER_URL = `${BASE_URL}/api/${API.VERSION}/user`;
    const headers  = { 'Content-Type': 'application/json', 'Authorization': `Bearer ${localStorage.getItem('accessToken')}` };

    let currentUserList     = [];
    let currentAddressUser  = null;
    let currentAddressList  = [];
    let currentSearchFilters = { userId: null, name: null, phoneNumber: null, email: null, birthDate: null, address: null, isBlacklisted: null, grade: null };
    let serverTotalPages    = 1;
    let currentSortFilters  = {};

    /* ─────────────────── 다중 선택 상태 ─────────────────── */
    let selectedIds = new Set(); // Set of u.id (Number)

    function updateBulkBar() {
      const bar   = document.getElementById('bulk-action-bar');
      const count = document.getElementById('bulk-count');
      if (selectedIds.size > 0) {
        bar.classList.add('visible');
        count.textContent = selectedIds.size;
      } else {
        bar.classList.remove('visible');
      }
    }

    window.toggleSelectAll = function (masterCb) {
      const checkboxes = document.querySelectorAll('.row-checkbox');
      checkboxes.forEach(cb => {
        cb.checked = masterCb.checked;
        const id = parseInt(cb.dataset.id, 10);
        const row = cb.closest('tr');
        if (masterCb.checked) { selectedIds.add(id); row.classList.add('selected-row'); }
        else                  { selectedIds.delete(id); row.classList.remove('selected-row'); }
      });
      updateBulkBar();
    };

    window.toggleRowCheckbox = function (cb, id) {
      const row = cb.closest('tr');
      if (cb.checked) { selectedIds.add(id); row.classList.add('selected-row'); }
      else            { selectedIds.delete(id); row.classList.remove('selected-row'); }

      // 전체 선택 체크박스 동기화
      const all  = document.querySelectorAll('.row-checkbox');
      const master = document.getElementById('select-all-checkbox');
      if (master) master.checked = all.length > 0 && [...all].every(c => c.checked);
      updateBulkBar();
    };

    window.clearAllSelections = function () {
      selectedIds.clear();
      document.querySelectorAll('.row-checkbox').forEach(cb => { cb.checked = false; cb.closest('tr').classList.remove('selected-row'); });
      const master = document.getElementById('select-all-checkbox');
      if (master) master.checked = false;
      updateBulkBar();
    };

    window.openBulkDeleteConfirmModal = function () {
      if (selectedIds.size === 0) { showToast('삭제할 유저를 선택해주세요.', true); return; }
      document.getElementById('bulk-delete-count').textContent = selectedIds.size;
      document.getElementById('bulk-delete-modal').style.display = 'flex';
    };

    window.closeBulkDeleteConfirmModal = function () {
      document.getElementById('bulk-delete-modal').style.display = 'none';
    };

    window.submitBulkDelete = async function () {
      const ids = [...selectedIds];
      try {
        const res = await Fetch(`${USER_URL}/delete/bulk`, {
          method: 'DELETE',
          headers,
          body: JSON.stringify({ userIds: ids })
        });

        if (res.ok) {
          showToast(`${ids.length}명의 유저가 삭제되었습니다.`);
          closeBulkDeleteConfirmModal();
          selectedIds.clear();
          updateBulkBar();
          const curr = parseInt(document.getElementById('pagination-current').value, 10) - 1;
          loadUserList(curr);
        } else {
          showToast('유저 일괄 삭제 처리 중 오류가 발생했습니다.', true);
        }
      } catch (e) {
        showToast('서버 통신 실패', true);
      }
    };

    /* ─────────────────── 목록 로드 ─────────────────── */
    window.loadUserList = async function (pageZeroIndexed = 0) {
      const pageSize  = parseInt(document.getElementById('pagination-size').value, 10);
      const sortArray = Object.keys(currentSortFilters).reduce((acc, field) => {
        if (currentSortFilters[field]) acc.push(`${field}-${currentSortFilters[field]}`);
        return acc;
      }, []);

      const cond = { ...currentSearchFilters, page: pageZeroIndexed, size: pageSize, sort: sortArray.length ? sortArray : null };

      try {
        const res = await Fetch(`${USER_URL}/select`, { method: 'POST', headers, body: JSON.stringify(cond) });
        if (!res.ok) { showToast('유저 목록 조회 실패', true); return; }

        const pagedModel = await res.json();
        currentUserList  = pagedModel.content || [];
        serverTotalPages = (pagedModel.page?.totalPages || pagedModel.totalPages || 1);
        if (serverTotalPages < 1) serverTotalPages = 1;
        const totalCount = pagedModel.totalElements ?? pagedModel.page?.totalElements ?? currentUserList.length;

        document.getElementById('pagination-total').textContent   = serverTotalPages;
        document.getElementById('pagination-current').value        = pageZeroIndexed + 1;
        document.getElementById('pagination-total-count').textContent = totalCount;

        // 페이지 전환 시 선택 초기화
        selectedIds.clear();
        updateBulkBar();
        const master = document.getElementById('select-all-checkbox');
        if (master) master.checked = false;

        const tbody = document.getElementById('user-table-body');
        tbody.innerHTML = '';

        currentUserList.forEach((u, index) => {
          const userIdAttr = escapeHtml(u.userId ?? '');
          const roleHtml   = u.role === 'ROLE_ADMIN'
            ? `<span class="badge badge-admin">ADMIN</span>`
            : `<span class="badge badge-user">USER</span>`;
          const statusHtml = u.isBlacklisted
            ? `<span class="badge badge-black">블랙</span>`
            : `<span class="badge badge-ok">정상</span>`;

          const grade = u.grade || 'GENERAL';
          const gradeHtml = `<span class="badge badge-grade badge-grade-${grade.toLowerCase()}">${escapeHtml(grade)}</span>`;

          const rowOrder = (pageZeroIndexed * pageSize) + (index + 1);
          const tr = document.createElement('tr');
          tr.setAttribute('data-pk', u.id);

          tr.innerHTML = `
            <td style="text-align:center;" onclick="event.stopPropagation()">
              <input type="checkbox" class="row-checkbox" data-id="${u.id}"
                     onclick="event.stopPropagation(); toggleRowCheckbox(this, ${u.id})">
            </td>
            <td style="text-align:center; color:var(--text-muted); font-size:12px;">${rowOrder}</td>
            <td><strong style="color:var(--text-primary);">${u.userId}</strong></td>
            <td>${u.name}</td>
            <td style="color:var(--text-secondary);">${u.email}</td>
            <td>${roleHtml}</td>
            <td>${statusHtml}</td>
            <td>${gradeHtml}</td>
            <td class="actions" onclick="event.stopPropagation()">
              <button type="button" class="btn btn-sm btn-address" data-user-id="${userIdAttr}" onclick="event.stopPropagation(); window.openUserAddressModal(this.dataset.userId)"><i class="ti ti-map-pin"></i>주소</button>
              <button type="button" class="btn btn-sm btn-outline" onclick="event.stopPropagation(); window.openModalForUpdate('${u.id}')">수정</button>
              <button type="button" class="btn btn-sm btn-danger"  onclick="event.stopPropagation(); window.openConfirmModalFromRow('${u.id}')">삭제</button>
            </td>
          `;

          tr.onclick = () => window.openModalForView(u.id);
          tbody.appendChild(tr);
        });

        syncSortHeaderUI();
      } catch (e) { showToast('서버 통신 오류', true); }
    };

    /* ─────────────────── 정렬 ─────────────────── */
    window.handleSortClick = function (headerEl, event) {
      const field   = headerEl.getAttribute('data-sort-field');
      const current = currentSortFilters[field];
      const next    = !current ? 'asc' : current === 'asc' ? 'desc' : null;

      if (!event.shiftKey) currentSortFilters = {};
      if (next) currentSortFilters[field] = next;
      else      delete currentSortFilters[field];

      loadUserList(0);
    };

    function syncSortHeaderUI() {
      document.querySelectorAll('.sortable').forEach(th => {
        const dir  = currentSortFilters[th.getAttribute('data-sort-field')];
        const icon = th.querySelector('.sort-icon');
        th.classList.remove('asc', 'desc');
        if (dir === 'asc')  { th.classList.add('asc');  icon.textContent = '▲'; }
        else if (dir === 'desc') { th.classList.add('desc'); icon.textContent = '▼'; }
        else icon.textContent = '↕';
      });
    }

    /* ─────────────────── 모달: VIEW ─────────────────── */
    window.openModalForView = function (id) {
      const user = currentUserList.find(u => u.id === parseInt(id, 10));
      if (!user) { showToast('유저 데이터를 찾을 수 없습니다.', true); return; }

      document.getElementById('m-modal-mode').value = 'VIEW';
      document.getElementById('modal-title').textContent    = '회원 정보 조회';
      document.getElementById('modal-subtitle').textContent = '읽기 전용 모드입니다.';

      document.querySelectorAll('#user-modal input, #user-modal select').forEach(el => {
        el.disabled = true;
        el.style.background = 'var(--bg)';
        el.style.color      = 'var(--text-muted)';
        el.style.cursor     = 'not-allowed';
      });

      _bindUserToModal(user);

      document.getElementById('wrapper-password-init').style.display = 'none';
      document.getElementById('btn-modal-submit').style.display      = 'none';
      document.getElementById('btn-modal-delete').style.display      = 'none';

      const actionRow = document.getElementById('modal-action-row');
      actionRow.style.display            = 'block';
      actionRow.style.gridTemplateColumns = 'none';

      const cancelBtn = document.querySelector('#user-modal .btn-secondary');
      if (cancelBtn) { cancelBtn.textContent = '닫기'; cancelBtn.style.width = '100%'; }

      document.getElementById('user-modal').style.display = 'flex';
    };

    /* ─────────────────── 모달: UPDATE ─────────────────── */
    window.openModalForUpdate = function (id) {
      const user = currentUserList.find(u => u.id === parseInt(id, 10));
      if (!user) { showToast('유저 데이터를 찾을 수 없습니다.', true); return; }

      document.getElementById('m-modal-mode').value = 'UPDATE';
      document.getElementById('modal-title').textContent    = '회원 정보 수정';
      document.getElementById('modal-subtitle').textContent = '정보를 수정한 뒤 저장 버튼을 눌러주세요.';

      document.querySelectorAll('#user-modal input, #user-modal select').forEach(el => {
        el.disabled = false;
        el.style.background = '#fafafa';
        el.style.color      = 'var(--text-primary)';
        el.style.cursor     = 'text';
      });

      const idInput = document.getElementById('m-user-id');
      if (idInput) { idInput.disabled = true; idInput.style.background = 'var(--bg)'; idInput.style.color = 'var(--text-muted)'; idInput.style.cursor = 'not-allowed'; }

      _bindUserToModal(user);

      const actionRow = document.getElementById('modal-action-row');
      actionRow.style.display             = 'grid';
      actionRow.style.gridTemplateColumns = 'repeat(2, 1fr)';

      document.getElementById('btn-modal-submit').style.display = 'block';
      document.getElementById('btn-modal-delete').style.display = 'none';
      document.getElementById('wrapper-password-init').style.display = 'block';

      const cancelBtn = document.querySelector('#user-modal .btn-secondary');
      if (cancelBtn) { cancelBtn.textContent = '취소'; cancelBtn.style.width = 'auto'; }

      document.getElementById('user-modal').style.display = 'flex';
    };

    /* ─────────────────── 모달: CREATE ─────────────────── */
    window.openModalForCreate = function () {
        document.getElementById('m-modal-mode').value = 'CREATE';
        document.getElementById('modal-title').textContent = '신규 유저 등록';
        document.getElementById('modal-subtitle').textContent =
            '새로운 유저를 등록합니다. 초기 비밀번호는 아이디+123! 로 설정됩니다.';
        document.getElementById('m-user-id-label').textContent = '아이디 (직접 입력)';

        document.querySelectorAll('#user-modal input, #user-modal select').forEach(el => {
            el.disabled = false;
            el.value = '';
            el.style.background = '#fafafa';
            el.style.color = 'var(--text-primary)';
            el.style.cursor = 'text';
        });

        document.getElementById('m-blacklist').value = 'false';
        document.getElementById('m-role').value = 'ROLE_USER';
        document.getElementById('m-grade').value = 'GENERAL';

        // 버튼 영역
        const actionRow = document.getElementById('modal-action-row');
        actionRow.style.display = 'grid';
        actionRow.style.gridTemplateColumns = 'repeat(2, 1fr)';

        // 등록 버튼
        const submitBtn = document.getElementById('btn-modal-submit');
        submitBtn.textContent = '등록하기';
        submitBtn.style.display = 'block';
        submitBtn.style.width = 'auto';

        // 닫기 버튼
        const cancelBtn = document.querySelector('#user-modal .btn-secondary');
        if (cancelBtn) {
            cancelBtn.style.display = 'block';
            cancelBtn.textContent = '닫기';
            cancelBtn.style.width = 'auto';
        }

        // 숨김 처리
        document.getElementById('btn-modal-delete').style.display = 'none';
        document.getElementById('wrapper-password-init').style.display = 'none';

        document.getElementById('user-modal').style.display = 'flex';
    };

    function _bindUserToModal(user) {
      _set('m-target-id', user.id);
      _set('m-user-id',   user.userId);
      _set('m-name',      user.name);
      _set('m-email',     user.email);
      _set('m-phone',     user.phoneNumber);
      _set('m-birth',     user.birthDate);
      _set('m-addr',      user.address);
      _set('m-role',      user.role || 'ROLE_USER');
      _set('m-grade',     user.grade || 'GENERAL');
      _set('m-blacklist', user.isBlacklisted ? 'true' : 'false');
    }

    function _set(id, val) {
      const el = document.getElementById(id);
      if (el) el.value = val || '';
    }

    window.closeModal = function () { document.getElementById('user-modal').style.display = 'none'; };

    function escapeHtml(value) {
      return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
    }

    function getAddressPayload(address, overrides = {}) {
      return {
        alias: overrides.alias ?? address.alias ?? null,
        recipientName: overrides.recipientName ?? address.recipientName ?? null,
        recipientPhone: overrides.recipientPhone ?? address.recipientPhone ?? null,
        zipCode: overrides.zipCode ?? address.zipCode ?? null,
        address: overrides.address ?? address.address ?? null,
        detailAddress: overrides.detailAddress ?? address.detailAddress ?? null,
        defaultAddress: overrides.defaultAddress ?? address.defaultAddress ?? false,
        status: overrides.status ?? address.status ?? 'ACTIVE'
      };
    }

    window.openUserAddressModal = async function (userId) {
      currentAddressUser = currentUserList.find(u => u.userId === userId) || { userId };

      document.getElementById('address-user-id-label').textContent = currentAddressUser.userId || '-';
      document.getElementById('address-user-name-label').textContent = currentAddressUser.name || '-';
      document.getElementById('address-user-email-label').textContent = currentAddressUser.email || '-';
      document.getElementById('address-card-list').innerHTML = '<div class="address-empty">배송지 정보를 불러오는 중입니다.</div>';
      document.getElementById('user-address-modal').style.display = 'flex';

      await loadUserAddressCards(userId);
    };

    window.closeUserAddressModal = function () {
      document.getElementById('user-address-modal').style.display = 'none';
      currentAddressUser = null;
      currentAddressList = [];
    };

    async function loadUserAddressCards(userId) {
      const cond = { page: 0, size: 20, sort: ['addressId-asc'] };

      try {
        const res = await Fetch(`${USER_URL}/address/select/user/${encodeURIComponent(userId)}`, {
          method: 'POST',
          headers,
          body: JSON.stringify(cond)
        });

        if (!res.ok) {
          document.getElementById('address-card-list').innerHTML = '<div class="address-empty error">배송지 조회에 실패했습니다.</div>';
          return;
        }

        const pagedModel = await res.json();
        currentAddressList = pagedModel.content || [];
        renderUserAddressCards();
      } catch (e) {
        document.getElementById('address-card-list').innerHTML = '<div class="address-empty error">서버 통신에 실패했습니다.</div>';
      }
    }

    function renderUserAddressCards() {
      const listEl = document.getElementById('address-card-list');
      if (!currentAddressList.length) {
        listEl.innerHTML = '<div class="address-empty">등록된 배송지가 없습니다.</div>';
        return;
      }

      listEl.innerHTML = currentAddressList.map(address => {
        const isDefault = address.defaultAddress === true;
        const isDeleted = address.status === 'DELETED';
        const fullAddress = `${address.address || ''} ${address.detailAddress || ''}`.trim();
        const defaultButton = !isDefault && !isDeleted
          ? `<button class="btn btn-sm btn-outline" onclick="window.setDefaultUserAddress(${address.addressId})">기본 설정</button>`
          : '';
        const disableButton = !isDeleted
          ? `<button class="btn btn-sm btn-danger" onclick="window.disableUserAddress(${address.addressId})">사용 중지</button>`
          : '';

        return `
          <article class="address-card ${isDeleted ? 'is-deleted' : ''}">
            <div class="address-card-header">
              <div>
                <strong class="address-card-title">${escapeHtml(address.alias || '배송지')}</strong>
                <span class="address-card-subtitle">#${escapeHtml(address.addressId)}</span>
              </div>
              <div class="address-badges">
                ${isDefault ? '<span class="address-default-badge">기본</span>' : ''}
                <span class="address-status-badge ${isDeleted ? 'deleted' : 'active'}">${escapeHtml(address.status || '-')}</span>
              </div>
            </div>
            <div class="address-card-body">
              <div class="address-row"><span>수령인</span><strong>${escapeHtml(address.recipientName || '-')}</strong></div>
              <div class="address-row"><span>연락처</span><strong>${escapeHtml(address.recipientPhone || '-')}</strong></div>
              <div class="address-row"><span>우편번호</span><strong>${escapeHtml(address.zipCode || '-')}</strong></div>
              <div class="address-row address-full"><span>주소</span><strong>${escapeHtml(fullAddress || '-')}</strong></div>
            </div>
            <div class="address-card-actions">
              <button class="btn btn-sm btn-outline" onclick="window.openUserAddressEditModal(${address.addressId})">수정</button>
              ${defaultButton}
              ${disableButton}
            </div>
          </article>
        `;
      }).join('');
    }

    window.openUserAddressEditModal = function (addressId) {
      const address = currentAddressList.find(item => item.addressId === Number(addressId));
      if (!address) { showToast('배송지 정보를 찾을 수 없습니다.', true); return; }

      _set('addr-edit-id', address.addressId);
      _set('addr-edit-alias', address.alias);
      _set('addr-edit-recipient-name', address.recipientName);
      _set('addr-edit-recipient-phone', address.recipientPhone);
      _set('addr-edit-zip-code', address.zipCode);
      _set('addr-edit-address', address.address);
      _set('addr-edit-detail-address', address.detailAddress);
      _set('addr-edit-status', address.status || 'ACTIVE');
      document.getElementById('addr-edit-default-address').checked = address.defaultAddress === true;
      document.getElementById('user-address-edit-modal').style.display = 'flex';
    };

    window.closeUserAddressEditModal = function () {
      document.getElementById('user-address-edit-modal').style.display = 'none';
    };

    window.submitUserAddressEdit = async function () {
      const addressId = Number(document.getElementById('addr-edit-id').value);
      const address = currentAddressList.find(item => item.addressId === addressId);
      if (!address) { showToast('배송지 정보를 찾을 수 없습니다.', true); return; }

      const body = getAddressPayload(address, {
        alias: document.getElementById('addr-edit-alias').value.trim() || null,
        recipientName: document.getElementById('addr-edit-recipient-name').value.trim() || null,
        recipientPhone: document.getElementById('addr-edit-recipient-phone').value.trim() || null,
        zipCode: document.getElementById('addr-edit-zip-code').value.trim() || null,
        address: document.getElementById('addr-edit-address').value.trim() || null,
        detailAddress: document.getElementById('addr-edit-detail-address').value.trim() || null,
        defaultAddress: document.getElementById('addr-edit-default-address').checked,
        status: document.getElementById('addr-edit-status').value || 'ACTIVE'
      });

      try {
        const res = await Fetch(`${USER_URL}/address/update/id/${addressId}`, {
          method: 'PUT',
          headers,
          body: JSON.stringify(body)
        });

        if (!res.ok) { showToast('배송지 수정에 실패했습니다.', true); return; }
        showToast('배송지가 수정되었습니다.');
        closeUserAddressEditModal();
        await loadUserAddressCards(currentAddressUser.userId);
      } catch (e) {
        showToast('서버 통신에 실패했습니다.', true);
      }
    };

    window.setDefaultUserAddress = async function (addressId) {
      const address = currentAddressList.find(item => item.addressId === Number(addressId));
      if (!address) { showToast('배송지 정보를 찾을 수 없습니다.', true); return; }

      try {
        const res = await Fetch(`${USER_URL}/address/update/id/${addressId}`, {
          method: 'PUT',
          headers,
          body: JSON.stringify(getAddressPayload(address, { defaultAddress: true, status: 'ACTIVE' }))
        });

        if (!res.ok) { showToast('기본 배송지 설정에 실패했습니다.', true); return; }
        showToast('기본 배송지가 변경되었습니다.');
        await loadUserAddressCards(currentAddressUser.userId);
      } catch (e) {
        showToast('서버 통신에 실패했습니다.', true);
      }
    };

    window.disableUserAddress = async function (addressId) {
      if (!confirm('해당 배송지를 사용 중지하시겠습니까?')) return;

      try {
        const res = await Fetch(`${USER_URL}/address/delete/id/${addressId}`, { method: 'DELETE', headers });
        if (!res.ok) { showToast('배송지 사용 중지에 실패했습니다.', true); return; }
        showToast('배송지가 사용 중지되었습니다.');
        await loadUserAddressCards(currentAddressUser.userId);
      } catch (e) {
        showToast('서버 통신에 실패했습니다.', true);
      }
    };

    /* ─────────────────── Form 제출 ─────────────────── */
    window.submitUserForm = async function () {
      const mode      = document.getElementById('m-modal-mode').value;
      const userIdVal = document.getElementById('m-user-id').value.trim();
      const nameVal   = (document.getElementById('m-name') || document.getElementById('m-username'))?.value.trim();

      const body = {
        name:          nameVal,
        phoneNumber:   document.getElementById('m-phone')?.value.trim()   || '',
        email:         document.getElementById('m-email')?.value.trim()   || '',
        birthDate:     document.getElementById('m-birth')?.value          || null,
        address:       document.getElementById('m-addr')?.value.trim()    || null,
        isBlacklisted: document.getElementById('m-blacklist')?.value === 'true',
        role:          document.getElementById('m-role')?.value           || 'ROLE_USER',
        grade:         document.getElementById('m-grade')?.value          || 'GENERAL',
      };

      if (mode === 'CREATE') {
        if (!userIdVal || !body.name) { showToast('아이디와 이름은 필수 입력 항목입니다.', true); return; }
        body.userId   = userIdVal;
        body.password = userIdVal + '123!';
      } else {
        if (!body.name) { showToast('이름은 필수 입력 항목입니다.', true); return; }
      }

      const url    = mode === 'CREATE' ? `${USER_URL}/insert` : `${USER_URL}/update/id/${document.getElementById('m-user-id').value}`;
      const method = mode === 'CREATE' ? 'POST' : 'PUT';

      try {
        const res = await Fetch(url, { method, headers, body: JSON.stringify(body) });
        if (res.ok) {
          showToast(mode === 'CREATE' ? '성공적으로 등록되었습니다.' : '성공적으로 수정되었습니다.');
          closeModal();
          const curr = mode === 'CREATE' ? 0 : parseInt(document.getElementById('pagination-current').value, 10) - 1;
          loadUserList(curr);
        } else { showToast('처리에 실패했습니다.', true); }
      } catch (e) { showToast('통신 장애', true); }
    };

    /* ─────────────────── 단건 삭제 ─────────────────── */
    window.openConfirmModalFromRow = function (id) {
      document.getElementById('confirm-target-id').value = id;
      document.getElementById('confirm-modal').style.display = 'flex';
    };
    window.openConfirmModalFromModal = function () {
      document.getElementById('confirm-target-id').value = document.getElementById('m-target-id').value;
      document.getElementById('confirm-modal').style.display = 'flex';
    };
    window.closeConfirmModal = function () { document.getElementById('confirm-modal').style.display = 'none'; };

    window.submitDeleteConfirm = async function () {
      const targetId = document.getElementById('confirm-target-id').value;
      try {
        const res = await Fetch(`${USER_URL}/delete/id/${targetId}`, { method: 'DELETE', headers });
        if (res.ok) {
          showToast('유저 정보가 삭제되었습니다.');
          closeConfirmModal(); closeModal();
          loadUserList(parseInt(document.getElementById('pagination-current').value, 10) - 1);
        } else { showToast('삭제 처리에 실패했습니다.', true); }
      } catch (e) { showToast('통신 장애', true); }
    };

    /* ─────────────────── 비밀번호 초기화 ─────────────────── */
    window.openPasswordInitConfirmModal  = function () { document.getElementById('pw-init-confirm-modal').style.display = 'flex'; };
    window.closePasswordInitConfirmModal = function () { document.getElementById('pw-init-confirm-modal').style.display = 'none'; };

    window.submitPasswordInit = async function () {
      const targetId = document.getElementById('m-target-id').value;
      try {
        const res = await Fetch(`${USER_URL}/init/password/${targetId}`, { method: 'PUT', headers });
        if (res.ok) { showToast('비밀번호가 "123456789!" 로 초기화되었습니다.'); closePasswordInitConfirmModal(); }
        else         { showToast('비밀번호 초기화 요청에 실패했습니다.', true); }
      } catch (e) { showToast('통신 장애', true); }
    };

    /* ─────────────────── 검색 ─────────────────── */
    window.triggerNormalSearch = function () {
      currentSearchFilters = { userId: document.getElementById('search-id').value.trim() || null, name: null, phoneNumber: null, email: null, birthDate: null, address: null, isBlacklisted: null, grade: null };
      loadUserList(0);
    };

    window.openSearchModal  = function () { document.getElementById('search-modal').style.display = 'flex'; };
    window.closeSearchModal = function () { document.getElementById('search-modal').style.display = 'none'; };

    window.resetUserSearch = function () {
      document.getElementById('search-id').value = '';
      document.querySelectorAll('#search-modal input').forEach(el => { el.value = ''; });
      document.querySelectorAll('#search-modal select').forEach(el => { el.selectedIndex = 0; });
      currentSearchFilters = { userId: null, name: null, phoneNumber: null, email: null, birthDate: null, address: null, isBlacklisted: null, grade: null };
      loadUserList(0);
    };

    window.submitDetailedSearch = function () {
      const isBlackVal = document.getElementById('cond-blacklist').value;
      const gradeVal = document.getElementById('cond-grade').value;
      currentSearchFilters = {
        userId:        document.getElementById('cond-id').value.trim()    || null,
        name:          document.getElementById('cond-name').value.trim()  || null,
        phoneNumber:   document.getElementById('cond-phone').value.trim() || null,
        email:         document.getElementById('cond-email').value.trim() || null,
        birthDate:     document.getElementById('cond-birth').value        || null,
        address:       document.getElementById('cond-addr').value.trim()  || null,
        isBlacklisted: isBlackVal === '' ? null : isBlackVal === 'true',
        grade:         gradeVal || null,
      };
      loadUserList(0);
      closeSearchModal();
    };

    /* ─────────────────── 초기화 ─────────────────── */
    window.Pagination.register({
      load: loadUserList,
      getTotalPages: function () { return serverTotalPages; }
    });
    loadUserList(0);
  })();
