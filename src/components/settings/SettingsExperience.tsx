'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { ARTWORK, INTRO_FLOWER, MODEL_FLOWER, PRIVACY_BOTTOM_FLOWER } from './artwork'
import {
  ArrowDownToLine, ArrowLeft, ArrowRight, BatteryFull, Bookmark, Brain,
  CalendarDays, Camera, Check, ChevronDown, ChevronRight, Clock3, Code2,
  Download, FileText, GraduationCap, Heart, Image as ImageIcon, Laptop, Leaf,
  Lightbulb, Link2, LockKeyhole, MessageCircle, MoreHorizontal, Palette, Plane,
  RotateCcw, Search, Settings2, ShieldCheck, SlidersHorizontal, Sparkles, Trash2,
  UserRound, Wifi, X, Zap, type LucideIcon,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react'

export type SettingsScreen = 'privacy' | 'history' | 'model'
type Tone = 'peach' | 'violet' | 'mint' | 'blue' | 'pink' | 'purple' | 'red'

const ROUTES: Record<SettingsScreen, string> = {
  privacy: '/settings/privacy', history: '/settings/history', model: '/settings/model',
}

function downloadJson(filename: string, value: unknown) {
  const blob = new Blob([JSON.stringify(value, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

function StatusBar() {
  return <div className="gs-status" aria-label="Device status"><strong>9:41</strong><span aria-hidden="true"><span className="gs-signal"><i /><i /><i /><i /></span><Wifi size={18} strokeWidth={2.6} /><BatteryFull size={24} strokeWidth={2.2} /></span></div>
}

function GlassIcon({ icon: Icon, tone = 'peach', size = 22 }: { icon: LucideIcon; tone?: Tone; size?: number }) {
  return <span className={`gs-icon gs-icon-${tone}`} aria-hidden="true"><Icon size={size} strokeWidth={1.85} /></span>
}

function Toggle({ checked, onChange, label }: { checked: boolean; onChange: (next: boolean) => void; label: string }) {
  return <button type="button" role="switch" aria-checked={checked} aria-label={label} className={`gs-toggle ${checked ? 'is-on' : ''}`} onClick={() => onChange(!checked)}><span /></button>
}

function Modal({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  const closeRef = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    closeRef.current?.focus()
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && onClose()
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('keydown', onKey); previous?.focus() }
  }, [onClose])
  return <div className="gs-modal-layer" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}><section className="gs-modal" role="dialog" aria-modal="true" aria-labelledby="gs-modal-title"><div className="gs-modal-head"><h2 id="gs-modal-title">{title}</h2><button ref={closeRef} type="button" className="gs-circle-button" onClick={onClose} aria-label="Close dialog"><X size={20} /></button></div>{children}</section></div>
}

function ScreenShell({ screen, title, subtitle, children, onBack }: { screen: SettingsScreen; title: string; subtitle: string; children: ReactNode; onBack?: () => void }) {
  const router = useRouter()
  return <div className={`gs-settings gs-settings-${screen}`} style={{ '--gs-backdrop': ARTWORK[screen].backdrop } as CSSProperties}>
    <div className="gs-art" aria-hidden="true" />
    <nav className="gs-desktop-nav" aria-label="Settings screens">{(['privacy', 'history', 'model'] as SettingsScreen[]).map((item) => <Link key={item} href={ROUTES[item]} className={item === screen ? 'is-active' : ''} aria-current={item === screen ? 'page' : undefined}>{item === 'privacy' ? <ShieldCheck size={17} /> : item === 'history' ? <MessageCircle size={17} /> : <Sparkles size={17} />}<span>{item === 'privacy' ? 'Privacy' : item === 'history' ? 'History' : 'Model'}</span></Link>)}</nav>
    <div className="gs-stage"><div className="gs-device"><StatusBar /><header className="gs-header"><button type="button" className="gs-back" onClick={onBack ?? (() => router.push(ROUTES.privacy))} aria-label="Go back"><ArrowLeft size={25} strokeWidth={1.8} /></button><div><h1>{title}</h1><p>{subtitle}</p></div></header><main className="gs-content">{children}</main></div></div>
  </div>
}

function SectionHeading({ title, hint }: { title: string; hint: string }) {
  return <div className="gs-section-heading"><h2>{title}</h2><span>{hint}</span></div>
}

function SettingsRow({ icon, tone, title, subtitle, onClick, trailing }: { icon: LucideIcon; tone: Tone; title: string; subtitle: string; onClick?: () => void; trailing?: ReactNode }) {
  return <button type="button" className="gs-row" onClick={onClick}><GlassIcon icon={icon} tone={tone} /><span className="gs-row-copy"><strong>{title}</strong><small>{subtitle}</small></span><span className="gs-row-trailing">{trailing ?? <ChevronRight size={19} />}</span></button>
}

function PrivacyScreen({ notify }: { notify: (message: string) => void }) {
  const router = useRouter()
  const [modal, setModal] = useState<string | null>(null)
  const [camera, setCamera] = useState(true)
  const [microphone, setMicrophone] = useState(false)
  const [location, setLocation] = useState(false)
  const [personalized, setPersonalized] = useState(true)
  const [connected, setConnected] = useState(false)
  const [retention, setRetention] = useState('90 days')
  const [deleteArmed, setDeleteArmed] = useState(false)
  const close = () => { setModal(null); setDeleteArmed(false) }
  return <ScreenShell screen="privacy" title="Privacy & Data Controls" subtitle="Your data, your control" onBack={() => router.push(ROUTES.model)}>
    <section className="gs-privacy-intro"><GlassIcon icon={ShieldCheck} tone="peach" size={31} /><div><h2>Your privacy matters</h2><p>Nexly is designed with your privacy in mind. You’re always in control of your data, conversations, and personal information.</p></div><span className="gs-flower gs-flower-intro" style={{ backgroundImage: INTRO_FLOWER }} aria-hidden="true" /></section>
    <section className="gs-section"><SectionHeading title="Data & Permissions" hint="Manage what Nexly can access" /><div className="gs-glass-card"><SettingsRow icon={Camera} tone="violet" title="App Permissions" subtitle="Camera, microphone, location, and more" onClick={() => setModal('permissions')} /><SettingsRow icon={UserRound} tone="mint" title="Personalization & Ads" subtitle="Control how your data is used for better experiences" onClick={() => setModal('personalization')} /><SettingsRow icon={Link2} tone="blue" title="Third-Party Integrations" subtitle="Manage connected services and apps" onClick={() => setModal('integrations')} /></div></section>
    <section className="gs-section"><SectionHeading title="Conversation & Activity" hint="Control your chat data" /><div className="gs-glass-card"><SettingsRow icon={MessageCircle} tone="peach" title="Chat History" subtitle="View, search, and manage your conversations" onClick={() => router.push(ROUTES.history)} /><SettingsRow icon={Search} tone="purple" title="Search History" subtitle="Manage your search activity" onClick={() => setModal('search')} /><SettingsRow icon={Clock3} tone="pink" title="Data Retention" subtitle="Choose how long we keep your data" onClick={() => setModal('retention')} /></div></section>
    <section className="gs-section"><SectionHeading title="Export & Deletion" hint="Download or delete your data" /><div className="gs-glass-card"><SettingsRow icon={ArrowDownToLine} tone="blue" title="Export Your Data" subtitle="Download a copy of your information" onClick={() => { downloadJson('nexly-data-export.json', { exportedAt: new Date().toISOString(), product: 'Nexly', conversations: 128, retention }); notify('Your data export has been downloaded.') }} /><SettingsRow icon={Trash2} tone="red" title="Delete Your Account" subtitle="Permanently remove your Nexly account" onClick={() => setModal('delete')} /></div></section>
    <footer className="gs-privacy-footer"><GlassIcon icon={LockKeyhole} tone="peach" size={21} /><div><strong>We respect your privacy</strong><p>Your data is encrypted, never sold, and only used to improve your Nexly experience.</p></div><span className="gs-flower gs-flower-privacy-bottom" style={{ backgroundImage: PRIVACY_BOTTOM_FLOWER }} aria-hidden="true" /></footer>
    {modal === 'permissions' && <Modal title="App Permissions" onClose={close}><p className="gs-modal-copy">Choose which device capabilities Nexly can access. Changes apply only in this browser preview.</p><div className="gs-modal-list">{[['Camera', camera, setCamera], ['Microphone', microphone, setMicrophone], ['Location', location, setLocation]].map(([label, value, setter]) => <div className="gs-preference" key={label as string}><span>{label as string}</span><Toggle checked={value as boolean} onChange={setter as (next: boolean) => void} label={`${label} permission`} /></div>)}</div></Modal>}
    {modal === 'personalization' && <Modal title="Personalization & Ads" onClose={close}><p className="gs-modal-copy">Use your activity to make recommendations feel more relevant. Nexly never sells your personal data.</p><div className="gs-preference"><span>Personalized experiences</span><Toggle checked={personalized} onChange={setPersonalized} label="Personalized experiences" /></div><button className="gs-primary-button" type="button" onClick={() => router.push(ROUTES.model)}>Open model preferences <ArrowRight size={17} /></button></Modal>}
    {modal === 'integrations' && <Modal title="Third-Party Integrations" onClose={close}><p className="gs-modal-copy">No external accounts are connected. You can preview the connection state without sharing credentials.</p><div className="gs-preference"><span>Calendar & reminders</span><Toggle checked={connected} onChange={setConnected} label="Calendar integration preview" /></div></Modal>}
    {modal === 'search' && <Modal title="Search History" onClose={close}><p className="gs-modal-copy">Search activity is stored locally in this browser. Clear it whenever you want.</p><button className="gs-primary-button" type="button" onClick={() => { notify('Search history cleared.'); close() }}>Clear search history</button></Modal>}
    {modal === 'retention' && <Modal title="Data Retention" onClose={close}><label className="gs-field-label" htmlFor="retention">Keep conversation data for</label><select id="retention" className="gs-select" value={retention} onChange={(event) => setRetention(event.target.value)}><option>30 days</option><option>90 days</option><option>1 year</option><option>Until I delete it</option></select><button className="gs-primary-button" type="button" onClick={() => { notify(`Retention set to ${retention}.`); close() }}>Save preference</button></Modal>}
    {modal === 'delete' && <Modal title="Delete Your Account" onClose={close}><p className="gs-modal-copy">This preview will not delete a real account. Confirm the interaction to see the protected account-deletion flow.</p><label className="gs-check-label"><input type="checkbox" checked={deleteArmed} onChange={(event) => setDeleteArmed(event.target.checked)} /> I understand this action is permanent.</label><button className="gs-danger-button" type="button" disabled={!deleteArmed} onClick={() => { notify('Account deletion request cancelled in preview mode.'); close() }}>Delete account</button></Modal>}
  </ScreenShell>
}

type Chat = { id: number; title: string; excerpt: string; date: string; icon: LucideIcon; tone: Tone; category: string }
const INITIAL_CHATS: Chat[] = [
  { id: 1, title: 'Study plan for next semester', excerpt: 'Here’s a personalized study plan based on your goals, available time, and preferred learning rhythm.', date: '9:12 AM', icon: GraduationCap, tone: 'peach', category: 'Planning' },
  { id: 2, title: 'Best laptop for coding in 2025', excerpt: 'Here are some of the best laptops for coding in 2025, compared by performance, battery, and value.', date: 'Yesterday', icon: Laptop, tone: 'blue', category: 'Research' },
  { id: 3, title: 'Travel plan for Japan', excerpt: 'Here’s a 7-day itinerary for Japan, including Tokyo, Kyoto, local food, and practical travel tips.', date: 'Sep 16', icon: Plane, tone: 'mint', category: 'Planning' },
  { id: 4, title: 'Logo design for my brand', excerpt: 'Here are 3 logo concepts for your brand, each with a distinct visual personality and color direction.', date: 'Sep 14', icon: Palette, tone: 'pink', category: 'Ideas' },
  { id: 5, title: 'Explain quantum computing simply', excerpt: 'Quantum computing uses the principles of quantum mechanics to explore many possibilities differently.', date: 'Sep 12', icon: Lightbulb, tone: 'peach', category: 'Research' },
  { id: 6, title: 'Workout and diet plan', excerpt: 'Here’s a balanced workout and diet plan tailored to your schedule, preferences, and weekly goals.', date: 'Sep 10', icon: Heart, tone: 'pink', category: 'Planning' },
  { id: 7, title: 'Project timeline and milestones', excerpt: 'Here’s a detailed project timeline with key milestones, owners, dependencies, and review points.', date: 'Sep 8', icon: FileText, tone: 'blue', category: 'Planning' },
]

function HistoryScreen({ notify }: { notify: (message: string) => void }) {
  const [chats, setChats] = useState(INITIAL_CHATS)
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('All')
  const [sortAsc, setSortAsc] = useState(false)
  const [savedOnly, setSavedOnly] = useState(false)
  const [saved, setSaved] = useState<Set<number>>(new Set([4]))
  const [menu, setMenu] = useState<number | null>(null)
  const [moreOpen, setMoreOpen] = useState(false)
  const [openChat, setOpenChat] = useState<Chat | null>(null)
  const [manage, setManage] = useState(false)
  const searchRef = useRef<HTMLInputElement>(null)
  const visible = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    const filtered = chats.filter((chat) => (!normalized || `${chat.title} ${chat.excerpt}`.toLowerCase().includes(normalized)) && (category === 'All' || chat.category === category) && (!savedOnly || saved.has(chat.id)))
    return sortAsc ? [...filtered].reverse() : filtered
  }, [chats, query, category, savedOnly, saved, sortAsc])
  const saveChat = (id: number) => {
    setSaved((current) => { const next = new Set(current); if (next.has(id)) next.delete(id); else next.add(id); return next })
    setMenu(null); notify('Saved conversations updated.')
  }
  return <ScreenShell screen="history" title="Chat History & Search" subtitle="Find, revisit, and manage your conversations">
    <div className="gs-searchbar"><Search size={22} /><input ref={searchRef} value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search your conversations..." aria-label="Search conversations" />{query && <button type="button" onClick={() => setQuery('')} aria-label="Clear search"><X size={17} /></button>}<button type="button" className="gs-filter-button" onClick={() => { setSortAsc((value) => !value); notify(sortAsc ? 'Newest conversations first.' : 'Oldest conversations first.') }} aria-label="Change conversation sort order"><SlidersHorizontal size={20} /></button></div>
    <div className="gs-chips" aria-label="Conversation categories">{['All', 'Chats', 'Research', 'Ideas', 'Planning'].map((item) => <button key={item} type="button" className={category === item ? 'is-active' : ''} onClick={() => { setCategory(item); setSavedOnly(false) }}>{item}</button>)}<div className="gs-more-wrap"><button type="button" onClick={() => setMoreOpen((value) => !value)} aria-expanded={moreOpen}>More <ChevronDown size={14} /></button>{moreOpen && <div className="gs-popover"><button type="button" onClick={() => { setSavedOnly((value) => !value); setMoreOpen(false) }}>{savedOnly ? 'Show all conversations' : 'Saved only'}</button><button type="button" onClick={() => { setSortAsc((value) => !value); setMoreOpen(false) }}>Reverse date order</button></div>}</div></div>
    <section className="gs-stats" aria-label="Conversation summary"><div><GlassIcon icon={MessageCircle} tone="peach" /><span><small>Total Conversations</small><strong>{chats.length === INITIAL_CHATS.length ? '128' : String(chats.length)}</strong><em>Your chat history with Nexly</em></span></div><div><GlassIcon icon={CalendarDays} tone="peach" /><span><small>Last Active</small><strong>Today, 9:12 AM</strong><em>You’re on a roll! <Sparkles size={11} /></em></span></div></section>
    <section className="gs-section gs-recent"><div className="gs-section-heading"><h2>Recent Chats</h2><button type="button" onClick={() => { setQuery(''); setCategory('All'); setSavedOnly(false) }}>View all <ArrowRight size={15} /></button></div>{manage && <div className="gs-manage-note">Manage mode is on. Use each conversation’s menu to save, archive, or delete it.<button type="button" onClick={() => setManage(false)}>Done</button></div>}<div className="gs-glass-card gs-chat-list">{visible.map((chat) => <div className="gs-chat-row" key={chat.id}><button type="button" className="gs-chat-open" onClick={() => setOpenChat(chat)}><GlassIcon icon={chat.icon} tone={chat.tone} size={21} /><span><strong>{chat.title}</strong><small>{chat.excerpt}</small></span></button><time>{chat.date}</time><div className="gs-chat-menu-wrap"><button type="button" className="gs-kebab" onClick={() => setMenu(menu === chat.id ? null : chat.id)} aria-label={`Actions for ${chat.title}`} aria-expanded={menu === chat.id}><MoreHorizontal size={18} /></button>{menu === chat.id && <div className="gs-popover gs-chat-popover"><button type="button" onClick={() => saveChat(chat.id)}>{saved.has(chat.id) ? 'Remove saved' : 'Save conversation'}</button><button type="button" onClick={() => { setChats((items) => items.filter((item) => item.id !== chat.id)); setMenu(null); notify('Conversation archived.') }}>Archive</button><button type="button" onClick={() => { setChats((items) => items.filter((item) => item.id !== chat.id)); setMenu(null); notify('Conversation deleted.') }}>Delete</button></div>}</div></div>)}{visible.length === 0 && <div className="gs-empty"><Search size={28} /><strong>No conversations found</strong><span>Try another search or clear your filters.</span><button type="button" onClick={() => { setQuery(''); setCategory('All'); setSavedOnly(false) }}>Clear filters</button></div>}</div></section>
    <section className="gs-section"><SectionHeading title="Quick Actions" hint="" /><div className="gs-quick-grid"><button type="button" onClick={() => searchRef.current?.focus()}><GlassIcon icon={Search} tone="peach" /><span><strong>Search Chats</strong><small>Find specific topics</small></span></button><button type="button" onClick={() => { setManage((value) => !value); notify(manage ? 'Manage mode closed.' : 'Manage mode enabled.') }}><GlassIcon icon={Trash2} tone="pink" /><span><strong>Manage History</strong><small>Delete or archive chats</small></span></button><button type="button" onClick={() => { setSavedOnly(true); setCategory('All'); setQuery(''); window.scrollTo({ top: 0, behavior: 'smooth' }) }}><GlassIcon icon={Bookmark} tone="purple" /><span><strong>Saved Conversations</strong><small>View your saved chats</small></span></button><button type="button" onClick={() => { downloadJson('nexly-chat-history.json', chats); notify('Chat history exported.') }}><GlassIcon icon={Download} tone="violet" /><span><strong>Export Chat History</strong><small>Download your data</small></span></button></div></section>
    <button type="button" className="gs-new-chat" onClick={() => { setQuery(''); setCategory('All'); setSavedOnly(false); searchRef.current?.focus(); notify('Start a new conversation whenever you’re ready.') }}><GlassIcon icon={Sparkles} tone="peach" /><span><strong>Start a new conversation</strong><small>Get back to what matters</small></span><span className="gs-new-chat-arrow"><ArrowRight size={22} /></span></button>
    {openChat && <Modal title={openChat.title} onClose={() => setOpenChat(null)}><div className="gs-conversation-preview"><GlassIcon icon={openChat.icon} tone={openChat.tone} /><p>{openChat.excerpt}</p></div><p className="gs-modal-copy">Last active · {openChat.date}</p><button type="button" className="gs-primary-button" onClick={() => { saveChat(openChat.id); setOpenChat(null) }}>{saved.has(openChat.id) ? 'Remove from saved' : 'Save conversation'}</button></Modal>}
  </ScreenShell>
}

const MODELS: { id: string; title: string; description: string; detail: string; icon: LucideIcon; tone: Tone; recommended?: boolean }[] = [
  { id: 'pro', title: 'Nexly Pro', description: 'Best balance of speed, intelligence and creativity.', detail: 'Great for most everyday tasks.', icon: Sparkles, tone: 'peach', recommended: true },
  { id: 'advanced', title: 'Nexly Advanced', description: 'Deep reasoning, complex problem solving and analysis.', detail: 'Ideal for research, coding and detailed tasks.', icon: Sparkles, tone: 'violet' },
  { id: 'code', title: 'Nexly Code', description: 'Specialized for programming and technical tasks.', detail: 'Great for developers and code-related work.', icon: Code2, tone: 'mint' },
  { id: 'creative', title: 'Nexly Creative', description: 'Focused on imagination, visuals and creative content.', detail: 'Best for design, writing and artistic tasks.', icon: ImageIcon, tone: 'pink' },
]
const EFFORTS = [
  { id: 'minimal', title: 'Minimal', description: 'Quick answers for simple questions.', time: '~ 1–2 seconds', icon: Zap, tone: 'peach' as Tone },
  { id: 'light', title: 'Light', description: 'Balanced speed and depth.', time: '~ 2–5 seconds', icon: Leaf, tone: 'mint' as Tone },
  { id: 'standard', title: 'Standard', description: 'Best for most tasks and questions.', time: '~ 5–10 seconds', icon: Sparkles, tone: 'peach' as Tone },
  { id: 'high', title: 'High', description: 'Deeper analysis and detailed reasoning.', time: '~ 10–30 seconds', icon: Brain, tone: 'violet' as Tone },
]

function ModelScreen({ notify }: { notify: (message: string) => void }) {
  const [model, setModel] = useState('pro')
  const [effort, setEffort] = useState('standard')
  const [auto, setAuto] = useState(false)
  const [remember, setRemember] = useState(true)
  const [hydrated, setHydrated] = useState(false)
  useEffect(() => {
    try {
      const stored = JSON.parse(localStorage.getItem('nexly-model-preferences') || 'null')
      if (stored) { setModel(stored.model ?? 'pro'); setEffort(stored.effort ?? 'standard'); setAuto(Boolean(stored.auto)); setRemember(stored.remember !== false) }
    } catch { /* Use defaults when storage is unavailable or invalid. */ }
    setHydrated(true)
  }, [])
  useEffect(() => {
    if (!hydrated || !remember) return
    try { localStorage.setItem('nexly-model-preferences', JSON.stringify({ model, effort, auto, remember })) } catch { /* Browser-only preference. */ }
  }, [model, effort, auto, remember, hydrated])
  return <ScreenShell screen="model" title="Model Selection & Reasoning Effort" subtitle="Choose the best model and how deeply Nexly thinks">
    <section className="gs-section gs-model-section"><SectionHeading title="AI Model" hint="Different models for different needs" /><div className="gs-glass-card gs-model-list" role="radiogroup" aria-label="AI model">{MODELS.map((item) => <button key={item.id} type="button" role="radio" aria-checked={model === item.id} className={`gs-model-row ${model === item.id ? 'is-selected' : ''}`} onClick={() => setModel(item.id)}><GlassIcon icon={item.icon} tone={item.tone} size={25} /><span><strong>{item.title}{item.recommended && <em>Recommended</em>}</strong><small>{item.description}</small><small>{item.detail}</small></span><span className="gs-radio">{model === item.id && <Check size={17} />}</span></button>)}</div></section>
    <section className="gs-section"><SectionHeading title="Reasoning Effort" hint="Control how much Nexly thinks" /><div className="gs-effort-grid" role="radiogroup" aria-label="Reasoning effort">{EFFORTS.map((item) => <button key={item.id} type="button" role="radio" aria-checked={effort === item.id} className={effort === item.id ? 'is-selected' : ''} onClick={() => setEffort(item.id)}><GlassIcon icon={item.icon} tone={item.tone} size={21} /><strong>{item.title}</strong><span>{item.description}</span><small>{item.time}</small><i className="gs-radio">{effort === item.id && <span />}</i></button>)}</div><div className="gs-effort-note"><Sparkles size={22} /><p>Higher reasoning effort helps Nexly provide more thoughtful, detailed and accurate responses — especially for complex questions.</p></div></section>
    <section className="gs-section"><SectionHeading title="Model Preferences" hint="Customize your experience" /><div className="gs-glass-card gs-preferences-card"><div className="gs-preference-row"><GlassIcon icon={Settings2} tone="peach" /><span><strong>Auto-select model</strong><small>Let Nexly choose the best model for each task.</small></span><Toggle checked={auto} onChange={setAuto} label="Auto-select model" /></div><div className="gs-preference-row"><GlassIcon icon={SlidersHorizontal} tone="pink" /><span><strong>Remember my choices</strong><small>Keep using your preferred model and reasoning effort.</small></span><Toggle checked={remember} onChange={(next) => { setRemember(next); if (!next) localStorage.removeItem('nexly-model-preferences') }} label="Remember my choices" /></div><button type="button" className="gs-reset-row" onClick={() => { setModel('pro'); setEffort('standard'); setAuto(false); setRemember(true); notify('Recommended model settings restored.') }}><GlassIcon icon={RotateCcw} tone="peach" /><span><strong>Reset to default</strong><small>Go back to Nexly’s recommended settings.</small></span><ChevronRight size={18} /></button></div></section>
    <footer className="gs-model-footer"><Sparkles size={25} /><div><strong>Smarter choices. Better conversations.</strong><p>The right model + the right thinking = the best Nexly experience.</p></div><span className="gs-flower gs-flower-model" style={{ backgroundImage: MODEL_FLOWER }} aria-hidden="true" /></footer>
  </ScreenShell>
}

export function SettingsExperience({ screen }: { screen: SettingsScreen }) {
  const [toast, setToast] = useState('')
  useEffect(() => {
    if (!toast) return
    const timeout = window.setTimeout(() => setToast(''), 3200)
    return () => window.clearTimeout(timeout)
  }, [toast])
  const notify = (message: string) => setToast(message)
  return <>{screen === 'privacy' && <PrivacyScreen notify={notify} />}{screen === 'history' && <HistoryScreen notify={notify} />}{screen === 'model' && <ModelScreen notify={notify} />}{toast && <div className="gs-toast" role="status"><Check size={17} />{toast}<button type="button" onClick={() => setToast('')} aria-label="Dismiss notification"><X size={15} /></button></div>}</>
}
