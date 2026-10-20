'use client'

/** The app kind dispatcher: type -> the 20 app experiences. */
import type { AppPayload } from '../types'
import { AppWizard } from '../apps/AppWizard'
import { AppOnboarding } from '../apps/AppOnboarding'
import { AppKanban } from '../apps/AppKanban'
import { AppCalendar } from '../apps/AppCalendar'
import { AppSheet } from '../apps/AppSheet'
import { AppTasks } from '../apps/AppTasks'
import { AppDashboard } from '../apps/AppDashboard'
import { AppConfig } from '../apps/AppConfig'
import { AppConfigurator } from '../apps/AppConfigurator'
import { AppBooking } from '../apps/AppBooking'
import { AppSchedule } from '../apps/AppSchedule'
import { AppSearch } from '../apps/AppSearch'
import { AppFilter } from '../apps/AppFilter'
import { AppAnalytics } from '../apps/AppAnalytics'
import { AppReport } from '../apps/AppReport'
import { AppFiles } from '../apps/AppFiles'
import { AppConnected } from '../apps/AppConnected'
import { AppDecision } from '../apps/AppDecision'
import { AppScenario } from '../apps/AppScenario'
import { AppTimer } from '../apps/AppTimer'

export function AppWidget({ payload }: { payload: AppPayload }) {
  switch (payload.type) {
    case 'wizard': return <AppWizard payload={payload} />
    case 'onboarding': return <AppOnboarding payload={payload} />
    case 'kanban': return <AppKanban payload={payload} />
    case 'calendar': return <AppCalendar payload={payload} />
    case 'sheet': return <AppSheet payload={payload} />
    case 'tasks': return <AppTasks payload={payload} />
    case 'dashboard': return <AppDashboard payload={payload} />
    case 'config': return <AppConfig payload={payload} />
    case 'configurator': return <AppConfigurator payload={payload} />
    case 'booking': return <AppBooking payload={payload} />
    case 'schedule': return <AppSchedule payload={payload} />
    case 'search': return <AppSearch payload={payload} />
    case 'filter': return <AppFilter payload={payload} />
    case 'analytics': return <AppAnalytics payload={payload} />
    case 'report': return <AppReport payload={payload} />
    case 'files': return <AppFiles payload={payload} />
    case 'connected': return <AppConnected payload={payload} />
    case 'decision': return <AppDecision payload={payload} />
    case 'scenario': return <AppScenario payload={payload} />
    case 'timer': return <AppTimer payload={payload} />
    default: return <AppWizard payload={payload} />
  }
}
