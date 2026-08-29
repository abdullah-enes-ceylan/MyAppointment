import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App'

// "!" -- index.html'de <div id="root"> her zaman var, Vite'in kendi
// sablonundaki standart giris noktasi deseni.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
