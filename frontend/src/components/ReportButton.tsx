import { useState } from 'react'
import { postReport } from '../api'

type Step = 'idle' | 'confirm' | 'sending' | 'sent' | 'error'

/**
 * "Someone's using this room", in two taps (ask, then confirm), so a stray tap on a phone doesn't
 * file a report. Sends nothing but the room id.
 */
export function ReportButton({ roomId, onReported }: { roomId: number; onReported: () => void }) {
  const [step, setStep] = useState<Step>('idle')
  const [message, setMessage] = useState('')

  const send = async () => {
    setStep('sending')
    try {
      await postReport(roomId)
      setStep('sent')
      onReported()
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Something went wrong.')
      setStep('error')
    }
  }

  switch (step) {
    case 'idle':
      return (
        <div className="report-box">
          <button type="button" className="button button--ghost" onClick={() => setStep('confirm')}>
            Room actually occupied?
          </button>
        </div>
      )
    case 'confirm':
    case 'sending':
      return (
        <div className="report-box" role="group" aria-label="Report this room">
          <p>Tell others someone is using this room right now? The warning disappears after an hour.</p>
          <div className="report-box__actions">
            <button type="button" className="button" disabled={step === 'sending'} onClick={send}>
              {step === 'sending' ? 'Sending…' : "Yes, it's occupied"}
            </button>
            <button type="button" className="button button--ghost" onClick={() => setStep('idle')}>Cancel</button>
          </div>
        </div>
      )
    case 'sent':
      return <p className="report-box" role="status">Thanks! Others will see a warning for the next hour.</p>
    case 'error':
      return <p className="report-box report-box--error" role="alert">{message}</p>
  }
}
