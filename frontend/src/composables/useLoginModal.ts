import { ref } from 'vue'

const showLoginModal = ref(false)

export function useLoginModal() {
  const open = () => {
    showLoginModal.value = true
  }

  const close = () => {
    showLoginModal.value = false
  }

  return {
    showLoginModal: showLoginModal,
    openLoginModal: open,
    closeLoginModal: close
  }
}