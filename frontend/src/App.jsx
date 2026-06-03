import {
  BookOpenText,
  CalendarDays,
  CircleHelp,
  FileClock,
  FileText,
  GraduationCap,
  LayoutDashboard,
  Menu,
  MessageCircleMore,
  PanelLeftClose,
  Plus,
  Settings,
  ShieldCheck,
  X,
} from 'lucide-react'
import { useState } from 'react'
import { NavLink, Route, Routes } from 'react-router-dom'
import AdminPage from './pages/AdminPage.jsx'
import ChatPage from './pages/ChatPage.jsx'
import React from 'react'

const navigation = [
  { to: '/', label: 'College Assistant', icon: MessageCircleMore },
  { to: '/documents', label: 'My Documents', icon: FileText },
  { to: '/calendar', label: 'Academic Calendar', icon: CalendarDays },
  { to: '/syllabus', label: 'Syllabus Explorer', icon: BookOpenText },
]

function Sidebar({ open, onClose }) {
  return (
    <aside className={`sidebar ${open ? 'is-open' : ''}`}>
      <div className="sidebar-brand">
        <span className="brand-mark"><GraduationCap size={21} /></span>
        <span><strong>VCE Assist</strong><small>College FAQ Portal</small></span>
        <button className="icon-button mobile-only" onClick={onClose} aria-label="Close menu"><X size={18} /></button>
      </div>

      <NavLink className="new-chat" to="/" onClick={onClose}><Plus size={17} /> New conversation</NavLink>

      <nav className="main-nav">
        <p className="nav-label">Explore</p>
        {navigation.map(({ to, label, icon: Icon }) => (
          <NavLink key={to} to={to} end={to === '/'} onClick={onClose}>
            <Icon size={17} /> {label}
          </NavLink>
        ))}
        <NavLink to="/admin" onClick={onClose}><ShieldCheck size={17} /> Admin panel</NavLink>
      </nav>

      <section className="recent-section">
        <p className="nav-label">Recent chats</p>
        <span>Examination schedule</span>
        <span>Semester holiday list</span>
        <span>Second year syllabus</span>
      </section>

      <footer className="sidebar-footer">
        <button><CircleHelp size={16} /> Help center</button>
        <button><Settings size={16} /> Settings</button>
        <div className="user-card">
          <span className="avatar">ST</span>
          <span><strong>Student portal</strong><small>Official VCE assistant</small></span>
        </div>
      </footer>
    </aside>
  )
}

function PlaceholderPage({ icon: Icon, title, description }) {
  return (
    <main className="placeholder-page">
      <Icon size={30} />
      <h1>{title}</h1>
      <p>{description}</p>
    </main>
  )
}

export default function App() {
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <div className="app-shell">
      <Sidebar open={menuOpen} onClose={() => setMenuOpen(false)} />
      <section className="content-shell">
        <header className="mobile-header">
          <button className="icon-button" onClick={() => setMenuOpen(true)} aria-label="Open menu"><Menu size={20} /></button>
          <strong>VCE Assist</strong>
          <PanelLeftClose size={19} />
        </header>
        <Routes>
          <Route path="/" element={<ChatPage />} />
          <Route path="/admin" element={<AdminPage />} />
          <Route path="/documents" element={<PlaceholderPage icon={FileText} title="My Documents" description="Your uploaded college documents will appear here." />} />
          <Route path="/calendar" element={<PlaceholderPage icon={FileClock} title="Academic Calendar" description="Ask the assistant for calendar dates and examination milestones." />} />
          <Route path="/syllabus" element={<PlaceholderPage icon={LayoutDashboard} title="Syllabus Explorer" description="Upload syllabus documents through the admin panel to search them." />} />
        </Routes>
      </section>
    </div>
  )
}
