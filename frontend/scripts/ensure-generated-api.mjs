import { existsSync, readdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(scriptDirectory, '..')

const requiredFiles = [
  'src/api/index.ts',
  'src/api/model/index.ts'
]

const requiredDirectories = [
  'src/api/endpoints',
  'src/api/model'
]

function directoryHasTypeScriptFile(relativePath) {
  const absolutePath = resolve(projectRoot, relativePath)

  if (!existsSync(absolutePath)) {
    return false
  }

  const entries = readdirSync(absolutePath, { withFileTypes: true })

  for (const entry of entries) {
    const nextRelativePath = `${relativePath}/${entry.name}`

    if (entry.isFile() && entry.name.endsWith('.ts')) {
      return true
    }

    if (entry.isDirectory() && directoryHasTypeScriptFile(nextRelativePath)) {
      return true
    }
  }

  return false
}

const missingFiles = requiredFiles.filter((relativePath) => !existsSync(resolve(projectRoot, relativePath)))
const missingDirectories = requiredDirectories.filter((relativePath) => !directoryHasTypeScriptFile(relativePath))

if (missingFiles.length === 0 && missingDirectories.length === 0) {
  process.exit(0)
}

const missingItems = [...missingFiles, ...missingDirectories]

console.error('Missing Orval-generated API client files.')
console.error('')
console.error('Expected generated artifacts:')
for (const item of missingItems) {
  console.error(`- ${item}`)
}
console.error('')
console.error('Start the backend at http://localhost:8080 and run:')
console.error('  pnpm run api:generate')
console.error('')
console.error('OpenAPI source:')
console.error('  http://localhost:8080/v3/api-docs')

process.exit(1)
