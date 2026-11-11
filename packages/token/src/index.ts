/** The raw tokens by name (the values live in tokens.css). Useful for charts, canvases and tests. */
export const colors = {
  teal: { 100: '#dbf2f3', 200: '#aedde1', 400: '#5bbac1', 600: '#2f8f98' },
  gray: { 0: '#ffffff', 50: '#f7f7f7', 100: '#f3f3f3', 300: '#dedede', 400: '#9a9a9a', 600: '#666666', 900: '#222222' },
  green: { 50: '#eaf8f0', 500: '#32b76f', 700: '#1f8a52' },
  orange: { 50: '#fdf1ec', 500: '#e7855b', 700: '#b85a33' },
  red: { 50: '#fdeef1', 500: '#d6455d' },
  blue: { 500: '#2f6fd4' },
} as const;

export const radii = { sm: 6, md: 8, lg: 10, full: 999 } as const;
