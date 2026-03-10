import { Node, mergeAttributes } from '@tiptap/vue-3'
import { resolveAssetUrl } from '@/utils/profile'

export const RichImage = Node.create({
  name: 'image',
  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,

  addAttributes() {
    return {
      src: {
        default: null,
        parseHTML: (element) => element.getAttribute('src'),
        renderHTML: (attributes) => {
          const rawSrc = typeof attributes.src === 'string' ? attributes.src : ''
          if (!rawSrc) {
            return {}
          }

          return {
            src: resolveAssetUrl(rawSrc) ?? rawSrc
          }
        }
      },
      alt: {
        default: null
      },
      title: {
        default: null
      }
    }
  },

  parseHTML() {
    return [
      {
        tag: 'img[src]'
      }
    ]
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'img',
      mergeAttributes(HTMLAttributes, {
        loading: 'lazy'
      })
    ]
  }
})
