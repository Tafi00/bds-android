#!/usr/bin/env node
/**
 * Generates app/src/main/assets/i18n/{en,zh,ru,ko}.json from the web app's dictionaries — the same
 * sources as the iOS String Catalog (bds-ios/scripts/generate-localizations.mjs):
 *   - bds-clone/src/i18n/workspace/{en,zh,ru,ko}.ts — keyed by the Vietnamese sentence, `{0}` placeholders;
 *   - bds-clone/src/i18n/locales/{vi,en,zh,ru,ko}   — keyed dictionaries, matched on the Vietnamese value;
 *   - bds-ios/scripts/i18n/ios-translations.json + parts/*.json — hand-written app sentences;
 *   - scripts/i18n/android-translations.json         — Android-only sentences (same `{ vi: { en, zh, ru, ko } }` shape).
 * The web wins over the app supplements; iOS supplements win over the Android one.
 *
 * At runtime `vn.futaland.app.core.i18n.I18n` looks the Vietnamese text up in the chosen language
 * (exact, then case-insensitive, then without a trailing `:`/`*`/`…`), so every `Text("…")` — literal or
 * not — follows the language switcher. Interpolated sentences go through `tr("Đã chọn {0} mục", n)`.
 *
 * Usage:  node bds-android/scripts/generate-localizations.mjs [--web ../bds-clone]
 *           [--audit <file.json>]  list Vietnamese Kotlin literals without a translation (with line numbers)
 * Needs `typescript` from bds-clone/node_modules (run `npm install` in bds-clone once).
 */
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const androidRoot = path.resolve(here, '..')
const args = process.argv.slice(2)
const argValue = (name, fallback) => {
    const index = args.indexOf(name)
    return index >= 0 && args[index + 1] ? path.resolve(args[index + 1]) : fallback
}
const webRoot = argValue('--web', path.resolve(androidRoot, '../bds-clone'))
const iosI18n = argValue('--ios-i18n', path.resolve(androidRoot, '../bds-ios/scripts/i18n'))
const auditPath = argValue('--audit', null)
const kotlinRoot = path.join(androidRoot, 'app/src/main/java')
const outputDir = path.join(androidRoot, 'app/src/main/assets/i18n')
const LANGUAGES = ['en', 'zh', 'ru', 'ko']

/**
 * Bare words that mean different things on different screens ("Nam" is both the gender "Male" and the
 * direction "South"). They are left out of the display-time dictionary: code translates them through
 * dedicated keys (`direction.*`, `gender.*`) instead, so property data such as `Nam` is never shown as "Male".
 */
const AMBIGUOUS = new Set(['Nam', 'Bắc', 'Đông', 'Tây', 'Nữ', 'Đông Nam', 'Đông Bắc', 'Tây Nam', 'Tây Bắc'])

// MARK: - Load the web's TypeScript dictionaries

const webRequire = createRequire(path.join(webRoot, 'package.json'))
let ts
try {
    ts = webRequire('typescript')
} catch {
    console.error(`Cannot load "typescript" from ${webRoot}/node_modules — run npm install in bds-clone first.`)
    process.exit(1)
}

const moduleCache = new Map()
function resolveTs(from, request) {
    const base = path.resolve(path.dirname(from), request)
    for (const candidate of [base, `${base}.ts`, `${base}.tsx`, path.join(base, 'index.ts')]) {
        if (fs.existsSync(candidate) && fs.statSync(candidate).isFile()) return candidate
    }
    throw new Error(`Cannot resolve ${request} from ${from}`)
}
function loadTs(file) {
    if (moduleCache.has(file)) return moduleCache.get(file).exports
    const module = { exports: {} }
    moduleCache.set(file, module)
    const source = fs.readFileSync(file, 'utf8')
    const { outputText } = ts.transpileModule(source, {
        compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
        fileName: file,
    })
    const localRequire = (request) => {
        if (!request.startsWith('.')) throw new Error(`Unexpected import ${request} in ${file}`)
        return loadTs(resolveTs(file, request))
    }
    vm.runInThisContext(`(function (exports, require, module) {${outputText}\n})`, { filename: file })(
        module.exports, localRequire, module,
    )
    return module.exports
}

const nfc = (value) => value.normalize('NFC')

/** Vietnamese sentence (NFC, `{n}` placeholders) → { en, zh, ru, ko }. */
const dictionary = new Map()
function addTranslation(vi, language, value, { override, allowSame = false }) {
    if (typeof vi !== 'string' || typeof value !== 'string') return
    if (!vi.trim() || !value.trim() || (value === vi && !allowSame)) return
    const key = nfc(vi)
    const entry = dictionary.get(key) ?? {}
    if (override || entry[language] === undefined) entry[language] = value
    dictionary.set(key, entry)
}

const locales = loadTs(path.join(webRoot, 'src/i18n/locales/index.ts'))
for (const [key, vi] of Object.entries(locales.vi)) {
    if (typeof vi !== 'string' || vi.includes('{')) continue
    for (const language of LANGUAGES) {
        const value = locales[language]?.[key]
        if (typeof value === 'string' && !value.includes('{')) addTranslation(vi, language, value, { override: false })
    }
}
for (const language of LANGUAGES) {
    const workspace = loadTs(path.join(webRoot, `src/i18n/workspace/${language}.ts`)).default
    for (const [vi, value] of Object.entries(workspace)) addTranslation(vi, language, value, { override: true })
}

const jsonFiles = (dir) => (fs.existsSync(dir)
    ? fs.readdirSync(dir).filter((name) => name.endsWith('.json')).sort().map((name) => path.join(dir, name))
    : [])
const supplementPaths = [
    path.join(iosI18n, 'ios-translations.json'),
    ...jsonFiles(path.join(iosI18n, 'parts')),
    path.join(here, 'i18n/android-translations.json'),
    ...jsonFiles(path.join(here, 'i18n/parts')),
]
for (const supplementPath of supplementPaths) {
    if (!fs.existsSync(supplementPath)) continue
    const supplement = JSON.parse(fs.readFileSync(supplementPath, 'utf8'))
    for (const [vi, values] of Object.entries(supplement)) {
        for (const language of LANGUAGES) addTranslation(vi, language, values[language], { override: false, allowSame: true })
    }
}
for (const word of AMBIGUOUS) dictionary.delete(word)

// MARK: - Write the assets

fs.mkdirSync(outputDir, { recursive: true })
for (const language of LANGUAGES) {
    const table = {}
    for (const key of [...dictionary.keys()].sort()) {
        const value = dictionary.get(key)[language]
        if (value) table[key] = value
    }
    fs.writeFileSync(path.join(outputDir, `${language}.json`), `${JSON.stringify(table)}\n`)
    console.log(`${language}: ${Object.keys(table).length} sentences`)
}

// MARK: - Audit Kotlin literals (mirrors the runtime lookup in I18n.kt)

if (auditPath) {
    const caseless = new Map()
    for (const [vi, entry] of dictionary) if (!caseless.has(vi.toLocaleLowerCase('vi'))) caseless.set(vi.toLocaleLowerCase('vi'), entry)
    const SUFFIX = /(\s*(?::|\(\*\)|\*|…|\.\.\.|\?|!)\s*|\s+)$/
    const find = (vi) => dictionary.get(nfc(vi)) ?? caseless.get(nfc(vi).toLocaleLowerCase('vi'))
    const translated = (vi) => {
        if (find(vi)) return true
        const trimmed = vi.trim()
        const match = trimmed.match(SUFFIX)
        const core = match && match.index > 0 ? trimmed.slice(0, match.index) : trimmed
        return core !== vi && Boolean(find(core))
    }
    const vietnamese = /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]/i
    const report = {}
    let total = 0
    for (const file of kotlinFiles(kotlinRoot)) {
        const source = fs.readFileSync(file, 'utf8')
        const seenInFile = new Set()
        for (const literal of kotlinLiterals(source)) {
            const vi = literal.parts.map((part, index) => (index === 0 ? part : `{${index - 1}}${part}`)).join('')
            if (!vietnamese.test(vi) || seenInFile.has(vi) || translated(vi)) continue
            seenInFile.add(vi)
            const relative = path.relative(kotlinRoot, file)
            ;(report[relative] ??= []).push({ line: source.slice(0, literal.start).split('\n').length, text: vi })
            total++
        }
    }
    fs.writeFileSync(auditPath, `${JSON.stringify(report, null, 2)}\n`)
    console.log(`Untranslated Vietnamese Kotlin literals: ${total} → ${auditPath}`)
}

function kotlinFiles(dir) {
    return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
        const full = path.join(dir, entry.name)
        if (entry.isDirectory()) return kotlinFiles(full)
        return entry.name.endsWith('.kt') ? [full] : []
    })
}

/** Minimal Kotlin lexer: yields single-line string literals as { parts, expressions, start }. */
function* kotlinLiterals(source) {
    let i = 0
    const n = source.length
    while (i < n) {
        const c = source[i]
        if (c === '/' && source[i + 1] === '/') { while (i < n && source[i] !== '\n') i++; continue }
        if (c === '/' && source[i + 1] === '*') { const end = source.indexOf('*/', i + 2); i = end < 0 ? n : end + 2; continue }
        if (c === "'") { i = source[i + 1] === '\\' ? source.indexOf("'", i + 3) + 1 : i + 3; continue }
        if (c === '"') {
            if (source.startsWith('"""', i)) { const end = source.indexOf('"""', i + 3); i = end < 0 ? n : end + 3; continue }
            const start = i
            const literal = readKotlinString(source, i + 1)
            i = literal.end
            if (literal.ok) yield { ...literal, start }
            continue
        }
        i++
    }
}

function readKotlinString(source, i) {
    const parts = ['']
    const expressions = []
    let ok = true
    while (i < source.length) {
        const c = source[i]
        if (c === '"') return { parts, expressions, ok, end: i + 1 }
        if (c === '\n') return { parts, expressions, ok: false, end: i }
        if (c === '\\') {
            const simple = { n: '\n', t: '\t', r: '\r', '"': '"', "'": "'", '\\': '\\', $: '$', b: '\b' }
            const next = source[i + 1]
            if (next in simple) { parts[parts.length - 1] += simple[next]; i += 2; continue }
            if (next === 'u') { parts[parts.length - 1] += String.fromCharCode(parseInt(source.slice(i + 2, i + 6), 16)); i += 6; continue }
            ok = false; i += 2; continue
        }
        if (c === '$' && source[i + 1] === '{') {
            let depth = 1
            let j = i + 2
            while (j < source.length && depth > 0) {
                if (source[j] === '"') { j = readKotlinString(source, j + 1).end; continue }
                if (source[j] === '{') depth++
                else if (source[j] === '}') depth--
                j++
            }
            expressions.push(source.slice(i + 2, j - 1))
            parts.push('')
            i = j
            continue
        }
        if (c === '$' && /[A-Za-z_]/.test(source[i + 1] ?? '')) {
            let j = i + 1
            while (j < source.length && /[A-Za-z0-9_]/.test(source[j])) j++
            expressions.push(source.slice(i + 1, j))
            parts.push('')
            i = j
            continue
        }
        parts[parts.length - 1] += c
        i++
    }
    return { parts, expressions, ok: false, end: i }
}
