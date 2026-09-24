import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import { ActorProvider } from './roles/ActorContext'
import { CalendarDataProvider } from './calendar/CalendarDataContext'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ActorProvider>
      <CalendarDataProvider>
        <App />
      </CalendarDataProvider>
    </ActorProvider>
  </React.StrictMode>,
)
