import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import AuthGate from './auth/AuthGate'
import { CalendarDataProvider } from './calendar/CalendarDataContext'
import { TaskDataProvider } from './tasks/TaskDataContext'
import { RewardDataProvider } from './rewards/RewardDataContext'
import { ShoppingDataProvider } from './shopping/ShoppingDataContext'
import { MealDataProvider } from './meals/MealDataContext'
import { ProfileCardProvider } from './profiles/ProfileCard'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AuthProvider>
      <AuthGate>
        <CalendarDataProvider>
          <TaskDataProvider>
            <RewardDataProvider>
              <ShoppingDataProvider>
                <MealDataProvider>
                  <ProfileCardProvider>
                    <App />
                  </ProfileCardProvider>
                </MealDataProvider>
              </ShoppingDataProvider>
            </RewardDataProvider>
          </TaskDataProvider>
        </CalendarDataProvider>
      </AuthGate>
    </AuthProvider>
  </React.StrictMode>,
)
