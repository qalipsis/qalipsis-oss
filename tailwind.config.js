/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './components/**/*.{js,vue,ts}',
    './utils/**/*.ts',
    './layouts/**/*.vue',
    './pages/**/*.vue',
    './plugins/**/*.{js,ts}',
    './app.vue',
    './error.vue',
  ],
  theme: {
    extend: {
      fontFamily: {
        'sans': ['Outfit', 'sans-serif']
      },
      transitionProperty: {
        'width': 'width',
      }
    },
    colors: {
      transparent: 'transparent',
      'white': '#FFFFFF',
      "primary": {
        50: "#EBF9F9",
        100: "#DBF5F5",
        200: "#B3EAEA",
        300: "#7FDBDB",
        400: "#41C9CA",
        500: "#34BBBB",
        600: "#2FA8A8",
        700: "#299494",
        800: "#227C7C",
        900: "#185858",
        950: "#124040"
      },
      'red': {
        50: '#FEF5F9',
        100: '#FEE7EF',
        200: '#FCCADC',
        300: '#FAADC9',
        400: '#F77DAA',
        500: '#F33278',
        600: '#E70E5D',
        700: '#CF0C54',
        800: '#A90A44',
        900: '#820835',
        950: '#520521'
      },
      'green': {
        50: '#EEFBF8',
        100: '#D9F7EF',
        200: '#ACECDB',
        300: '#75E0C4',
        400: '#2ED1A5',
        500: '#28B48F',
        600: '#24A381',
        700: '#208E70',
        800: '#1A755D',
        900: '#135342',
        950: '#0E3F32'
      },
      'gray': {
        50: '#F7F8F8',
        100: '#ECEEEE',
        200: '#DCDFDF',
        300: '#C7CBCC',
        400: '#AFB5B6',
        500: '#939B9D',
        600: '#848D8F',
        700: '#727B7E',
        800: '#5F6668',
        900: '#414748',
        950: '#2E3233'
      },
      'purple': {
        50: '#F7F1FE',
        100: '#F1E8FD',
        200: '#E1CBFB',
        300: '#CDABF8',
        400: '#B480F4',
        500: '#8C3CEE',
        600: '#8027EC',
        700: '#6E13DD',
        800: '#5B10B7',
        900: '#410B83',
        950: '#25074B'
      },
      'yellow': {
        50: '#FFFBF0',
        100: '#FFF5DB',
        200: '#FFEBB8',
        300: '#FFDF8F',
        400: '#FFD25E',
        500: '#FFBB0F',
        600: '#EBA800',
        700: '#CC9200',
        800: '#AD7C00',
        900: '#7A5800',
        950: '#5C4200'
      }
    }
  },
  plugins: [],
}