import type { NexlyPlan, NexlyThinking } from './plan'

/**
 * The response from the reference sheet, expressed in the wire contract.
 * Nothing here is an emoji: the closing "flex" is a glass flame glyph.
 */
export const NEXLY_DEMO_USER = {
  text: 'Can you help me plan a productive day tomorrow? I have classes, assignments and also want to stay healthy and get some rest.',
  time: '9:41 PM',
  delivered: true,
}

export const NEXLY_DEMO_PREAMBLE = `Got it! I'll create a balanced plan for your tomorrow — focused on studies, productivity, health and rest.

Let me think for a moment...`

export const NEXLY_DEMO_THINKING: NexlyThinking = {
  active: true,
  rows: [
    {
      icon: 'sparkles',
      title: 'Thinking',
      subtitle: 'Analyzing your goals and priorities...',
      tone: 'peach',
    },
    {
      icon: 'brain',
      title: 'Reasoning',
      subtitle: 'Planning the best structure and balance...',
      tone: 'lavender',
    },
    {
      icon: 'settings',
      title: 'Effort',
      subtitle: 'Creating a detailed, realistic schedule...',
      tone: 'mint',
    },
  ],
}

export const NEXLY_DEMO_PLAN: NexlyPlan = {
  title: "Here's your personalized plan for tomorrow:",
  rows: [
    { time: '8:00 – 8:30 AM', icon: 'sunrise', text: 'Morning routine + light exercise', tone: 'amber' },
    { time: '9:00 – 11:00 AM', icon: 'book-2', text: 'Classes', tone: 'lavender' },
    { time: '11:30 – 1:00 PM', icon: 'device-laptop', text: 'Assignment / Study time', tone: 'blue' },
    { time: '1:00 – 2:00 PM', icon: 'tools-kitchen-2', text: 'Lunch + short break', tone: 'green' },
    { time: '2:00 – 4:00 PM', icon: 'file-text', text: 'Continue assignments', tone: 'lavender' },
    { time: '4:00 – 5:00 PM', icon: 'run', text: 'Workout / Physical activity', tone: 'pink' },
    { time: '5:00 – 7:00 PM', icon: 'device-gamepad-2', text: 'Free time / Hobbies', tone: 'blue' },
    { time: '7:00 – 9:00 PM', icon: 'book-2', text: 'Light study / Review', tone: 'lavender' },
    { time: '9:30 – 10:30 PM', icon: 'moon', text: 'Wind down + Sleep', tone: 'lilac' },
  ],
  closing: 'You got this, Arafat!',
  closingIcon: 'flame',
  timestamp: '9:45 PM',
}

/** The raw wire form, exactly as the model would emit it. */
export const NEXLY_DEMO_WIRE = `Got it! I'll create a balanced plan for your tomorrow — focused on studies, productivity, health and rest.

Let me think for a moment...

\`\`\`plan
${JSON.stringify(NEXLY_DEMO_PLAN, null, 2)}
\`\`\``
