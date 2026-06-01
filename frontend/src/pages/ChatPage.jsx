import {
  ArrowUp,
  CalendarDays,
  FileText,
  GraduationCap,
  MessageSquareText,
  Paperclip,
  Sparkles,
  ThumbsDown,
  ThumbsUp,
} from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { v4 as uuidv4 } from 'uuid'
import api from '../api.js'

const SESSION_KEY = 'vce-assist-session-id'
const suggestions = [
  { icon: CalendarDays, text: 'When are the upcoming college holidays?' },
  { icon: GraduationCap, text: 'Show me the examination schedule' },
  { icon: FileText, text: 'What subjects are in the second year syllabus?' },
  { icon: MessageSquareText, text: 'What are the important academic dates?' },
]

function getSessionId() {
  const existing = localStorage.getItem(SESSION_KEY)
  if (existing) return existing
  const sessionId = uuidv4()
  localStorage.setItem(SESSION_KEY, sessionId)
  return sessionId
}

export default function ChatPage() {
  const [sessionId] = useState(getSessionId)
  const [messages, setMessages] = useState([])
  const [question, setQuestion] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const messageEnd = useRef(null)

  useEffect(() => {
    api.get(`/chat/history/${sessionId}`)
      .then(({ data }) => setMessages(data))
      .catch(() => setError('Could not load earlier messages. The backend may be offline.'))
  }, [sessionId])

  useEffect(() => {
    messageEnd.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  async function askQuestion(event, suggestedQuestion) {
    event?.preventDefault()
    const prompt = (suggestedQuestion || question).trim()
    if (!prompt || loading) return

    setMessages((current) => [...current, { role: 'USER', content: prompt }])
    setQuestion('')
    setError('')
    setLoading(true)
    try {
      const { data } = await api.post('/chat', { sessionId, question: prompt })
      setMessages((current) => [...current, {
        id: data.messageId,
        role: 'ASSISTANT',
        content: data.answer,
        sources: data.sources,
      }])
    } catch (requestError) {
      setError(requestError.response?.data?.error || 'Could not generate an answer. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  async function sendFeedback(messageId, rating) {
    if (!messageId) return
    try {
      await api.post('/chat/feedback', { messageId, rating })
      setMessages((current) => current.map((message) => (
        message.id === messageId ? { ...message, rating } : message
      )))
    } catch {
      setError('Could not save your feedback.')
    }
  }

  return (
    <main className={`chat-page ${messages.length > 0 ? 'has-messages' : ''}`}>
      <section className="chat-workspace">
        {messages.length === 0 ? (
          <div className="welcome-state">
            <span className="assistant-orb"><Sparkles size={27} /></span>
            <p className="welcome-note">Welcome to your college assistant</p>
            <h1>How can I help you today?</h1>
            <p className="welcome-copy">Ask about academic calendars, examination schedules, holidays, syllabus details, or any information available in official college documents.</p>
          </div>
        ) : (
          <div className="conversation-header">
            <p>Official college assistant</p>
            <h1>College information chat</h1>
          </div>
        )}

        {messages.length > 0 && (
          <section className="message-list">
            {messages.map((message, index) => (
              <article className={`message-row ${message.role.toLowerCase()}`} key={`${message.id || index}-${message.role}`}>
                <span className="message-avatar">{message.role === 'USER' ? 'ST' : <Sparkles size={16} />}</span>
                <div>
                  <div className="message-bubble">{message.content}</div>
                  {message.role === 'ASSISTANT' && (
                    <div className="message-details">
                      {message.sources?.length > 0 && <p>Sources: {message.sources.join(', ')}</p>}
                      <div className="feedback-actions">
                        <button className={message.rating === 'THUMBS_UP' ? 'selected' : ''} onClick={() => sendFeedback(message.id, 'THUMBS_UP')} aria-label="Helpful answer"><ThumbsUp size={13} /></button>
                        <button className={message.rating === 'THUMBS_DOWN' ? 'selected' : ''} onClick={() => sendFeedback(message.id, 'THUMBS_DOWN')} aria-label="Answer needs work"><ThumbsDown size={13} /></button>
                      </div>
                    </div>
                  )}
                </div>
              </article>
            ))}
            {loading && (
              <article className="message-row assistant">
                <span className="message-avatar"><Sparkles size={16} /></span>
                <div className="message-bubble typing">Searching the college knowledge base...</div>
              </article>
            )}
            <div ref={messageEnd} />
          </section>
        )}

        <section className="composer-area">
          {error && <p className="error-banner">{error}</p>}
          <form className="question-composer" onSubmit={askQuestion}>
            <button type="button" className="composer-icon" aria-label="Attach document"><Paperclip size={18} /></button>
            <input value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="Ask anything about your college..." />
            <button className="send-button" disabled={loading || !question.trim()} aria-label="Send question"><ArrowUp size={18} /></button>
          </form>
          {messages.length === 0 && (
            <div className="suggestion-grid">
              {suggestions.map(({ icon: Icon, text }) => (
                <button key={text} onClick={(event) => askQuestion(event, text)}><Icon size={15} /> {text}</button>
              ))}
            </div>
          )}
          <p className="disclaimer">VCE Assist uses official uploaded documents. Confirm critical information with the college office.</p>
        </section>
      </section>
    </main>
  )
}
