import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import AuthGate from './auth/AuthGate'
import { CalendarDataProvider } from './calendar/CalendarDataContext'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AuthProvider>
      <AuthGate>
        <CalendarDataProvider>
          <App />
        </CalendarDataProvider>
      </AuthGate>
    </AuthProvider>
  </React.StrictMode>,
)
