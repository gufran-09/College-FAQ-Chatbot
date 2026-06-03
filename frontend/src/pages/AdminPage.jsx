import { FilePlus2, FileText, RefreshCw, ShieldCheck, Trash2, UploadCloud } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import api from '../api.js'
import React from 'react'

export default function AdminPage() {
  const [documents, setDocuments] = useState([])
  const [file, setFile] = useState(null)
  const [uploadedBy, setUploadedBy] = useState('admin')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const loadDocuments = useCallback(async () => {
    try {
      const { data } = await api.get('/admin/documents')
      setDocuments(data)
      setError('')
    } catch {
      setError('Could not load documents. Check that the backend is running.')
    }
  }, [])

  useEffect(() => {
    loadDocuments()
    const intervalId = setInterval(loadDocuments, 5000)
    return () => clearInterval(intervalId)
  }, [loadDocuments])

  async function upload(event) {
    event.preventDefault()
    if (!file) return
    setLoading(true)
    setError('')
    setNotice('')

    const formData = new FormData()
    formData.append('file', file)
    formData.append('uploadedBy', uploadedBy)
    try {
      await api.post('/admin/upload', formData)
      setNotice('Document uploaded. Indexing has started in the background.')
      setFile(null)
      event.target.reset()
      await loadDocuments()
    } catch (requestError) {
      setError(requestError.response?.data?.error || 'Upload failed.')
    } finally {
      setLoading(false)
    }
  }

  async function remove(documentId) {
    if (!window.confirm('Delete this document and its indexed chunks?')) return
    setError('')
    setNotice('')
    try {
      await api.delete(`/admin/documents/${documentId}`)
      setNotice('Document removed from the knowledge base.')
      await loadDocuments()
    } catch {
      setError('Could not delete the document.')
    }
  }

  return (
    <main className="admin-page">
      <header className="admin-heading">
        <span className="section-icon"><ShieldCheck size={20} /></span>
        <div>
          <p>Knowledge base control</p>
          <h1>Document administration</h1>
          <span>Upload official documents for the assistant to search and cite.</span>
        </div>
      </header>

      <section className="admin-grid">
        <form className="admin-card upload-card" onSubmit={upload}>
          <div className="card-title">
            <span><FilePlus2 size={18} /></span>
            <div><h2>Add a document</h2><p>PDF and DOCX files up to 50MB</p></div>
          </div>
          <label className="drop-zone">
            <UploadCloud size={26} />
            <strong>{file ? file.name : 'Choose an official college file'}</strong>
            <span>Click to select a PDF or DOCX document</span>
            <input type="file" accept=".pdf,.docx" onChange={(event) => setFile(event.target.files[0])} required />
          </label>
          <label className="field-label">
            Uploaded by
            <input value={uploadedBy} onChange={(event) => setUploadedBy(event.target.value)} required />
          </label>
          <button className="admin-primary" disabled={loading}>{loading ? 'Uploading...' : 'Upload and index document'}</button>
        </form>

        <section className="admin-card stats-card">
          <div className="card-title">
            <span><FileText size={18} /></span>
            <div><h2>Knowledge base</h2><p>Current indexing overview</p></div>
          </div>
          <div className="stats-grid">
            <div><strong>{documents.length}</strong><span>Total documents</span></div>
            <div><strong>{documents.filter((document) => document.status === 'COMPLETED').length}</strong><span>Ready to search</span></div>
            <div><strong>{documents.reduce((total, document) => total + (document.totalChunks || 0), 0)}</strong><span>Indexed chunks</span></div>
          </div>
        </section>
      </section>

      {error && <p className="error-banner admin-alert">{error}</p>}
      {notice && <p className="notice-banner">{notice}</p>}

      <section className="admin-card document-card">
        <div className="table-heading">
          <div><h2>Indexed documents</h2><p>Status updates automatically every five seconds.</p></div>
          <button className="refresh-button" onClick={loadDocuments}><RefreshCw size={15} /> Refresh</button>
        </div>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Document</th><th>Type</th><th>Status</th><th>Chunks</th><th>Uploaded by</th><th /></tr></thead>
            <tbody>
              {documents.map((document) => (
                <tr key={document.id}>
                  <td><span className="document-name"><FileText size={15} /> {document.fileName}</span></td>
                  <td>{document.fileType}</td>
                  <td><span className={`status ${document.status.toLowerCase()}`}>{document.status}</span></td>
                  <td>{document.totalChunks ?? '-'}</td>
                  <td>{document.uploadedBy}</td>
                  <td><button className="delete-button" onClick={() => remove(document.id)} aria-label={`Delete ${document.fileName}`}><Trash2 size={15} /></button></td>
                </tr>
              ))}
              {documents.length === 0 && <tr><td colSpan="6" className="empty-table">No documents have been uploaded yet.</td></tr>}
            </tbody>
          </table>
        </div>
      </section>
    </main>
  )
}
