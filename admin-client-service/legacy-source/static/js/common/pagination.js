(function () {
  let loadPage = null;
  let getTotalPages = function () { return 1; };

  window.Pagination = {
    register: function (options) {
      loadPage = options.load;
      getTotalPages = options.getTotalPages || getTotalPages;
    }
  };

  window.handleSizeChange = function () {
    if (loadPage) loadPage(0);
  };

  window.navigatePage = function (offset) {
    const inputEl = document.getElementById('pagination-current');
    let curr = parseInt(inputEl.value, 10) || 1;
    let target = Math.min(Math.max(curr + offset, 1), getTotalPages());
    if (curr !== target && loadPage) {
      inputEl.value = target;
      loadPage(target - 1);
    }
  };

  window.handlePageKeydown = function (e) {
    if (e.key === 'Enter') {
      e.preventDefault();
      _processPageJump();
    }
  };

  window.handlePageInputChange = function () {
    _processPageJump();
  };

  function _processPageJump() {
    const inputEl = document.getElementById('pagination-current');
    let target = parseInt(inputEl.value, 10);
    if (isNaN(target) || target < 1) target = 1;
    else if (target > getTotalPages()) target = getTotalPages();
    inputEl.value = target;
    if (loadPage) loadPage(target - 1);
  }
})();
