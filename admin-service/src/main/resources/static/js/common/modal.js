window.Modal = {
  open(id) {
    const modal = document.getElementById(id);
    if (modal) modal.style.display = 'flex';
  },
  close(id) {
    const modal = document.getElementById(id);
    if (modal) modal.style.display = 'none';
  }
};

function getVisibleModals() {
  return [...document.querySelectorAll('.d-modal, .f-modal')]
    .filter(modal => getComputedStyle(modal).display !== 'none');
}

function getModalZIndex(modal) {
  const zIndex = Number.parseInt(getComputedStyle(modal).zIndex, 10);
  return Number.isNaN(zIndex) ? 0 : zIndex;
}

document.addEventListener('keydown', event => {
  if (event.key !== 'Escape') return;

  const visibleModals = getVisibleModals();
  if (visibleModals.length === 0) return;

  const topModal = visibleModals.sort((a, b) => getModalZIndex(b) - getModalZIndex(a))[0];
  topModal.style.display = 'none';
  event.preventDefault();
});
