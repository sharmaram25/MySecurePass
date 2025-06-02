# MySecurePass - Frontend

The frontend for the MySecurePass application is built with modern web technologies to provide a smooth, intuitive user experience.

## Technology Stack

- **React**: A JavaScript library for building user interfaces
- **Vite**: Next-generation frontend tooling for faster development
- **Tailwind CSS**: A utility-first CSS framework for rapid UI development
- **Axios**: Promise-based HTTP client for API requests

## Features

- **Responsive Design**: Works seamlessly across desktop, tablet, and mobile devices
- **Light/Dark Mode**: User-selectable theme preference
- **Animated Security Background**: Dynamic visual elements that reinforce security concepts
- **Modern UI**: Clean, intuitive interface with smooth transitions and visual feedback

## Development

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

## Project Structure

```
src/
├── assets/           # Static assets like images and SVGs
├── components/       # Reusable UI components
├── css/              # CSS files including Tailwind customizations
├── service/          # API service integration
├── App.jsx           # Main application component
└── main.jsx          # Application entry point
```

## Integration with Backend

The frontend communicates with the Spring Boot backend via RESTful API endpoints for:

- Password generation
- Password strength verification
- Email delivery

See the main [README.md](../../README.md) for full project documentation.

---

© 2025 MySecurePass | Created by Ram Sharma
