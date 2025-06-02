# MySecurePass - Advanced Password Generator

![Dark Mode](Dark.png)
![Light Mode](Light.png)

## Overview

MySecurePass is a modern, user-friendly password generation tool designed to enhance your online security. In today's digital landscape, strong passwords are your first line of defense against unauthorized access and data breaches. This application provides a simple yet powerful solution for creating and managing secure passwords.

## Features

- **Customizable Password Generation**: Tailor passwords to your specific requirements.
- **Security Options**: Include uppercase letters, lowercase letters, numbers, and special characters.
- **Flexible Length**: Generate passwords from 8 to 24 characters.
- **Password Strength Verification**: Real-time feedback on password strength.
- **Smart Suggestions**: One-click strong password generation.
- **Email Delivery**: Send generated passwords directly to your inbox.
- **Responsive Design**: Beautiful UI that works across devices.
- **Light/Dark Mode**: Choose your preferred theme.
- **Animated Security Background**: Visual reinforcement of security concepts.

## Getting Started

### Prerequisites

- Java 17 or higher
- Node.js 14 or higher
- npm or yarn

### Installation

1. Clone the repository
```bash
git clone https://github.com/sharmaram25/MySecurePass.git
cd MySecurePass
```

2. Backend Setup
```bash
# Navigate to the project root
cd MySecurePass

# Build the Spring Boot application
mvn clean install

# Run the backend server
mvn spring-boot:run
```

3. Frontend Setup
```bash
# Navigate to the UI directory
cd ui/password-generator-tool

# Install dependencies
npm install

# Run the development server
npm run dev
```

4. Access the application
```
Backend: http://localhost:5000
Frontend: http://localhost:3000 (or another port assigned by Vite)
```

## Email Configuration

To enable the email functionality:

1. Open `src/main/resources/application.properties`
2. Update the following properties with your email credentials:
```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
```

For Gmail, you'll need to use an App Password if you have 2FA enabled.

## Technologies Used

### Backend
- **Spring Boot**: Robust Java framework for building the API
- **Spring Mail**: Email service integration
- **Passay**: Password validation and generation library
- **Maven**: Dependency management and build tool

### Frontend
- **React**: UI component library
- **Vite**: Build tool and development server
- **Tailwind CSS**: Utility-first CSS framework
- **Axios**: HTTP client for API requests

## Project Structure

```
MySecurePass/
├── src/                  # Backend source files
│   └── main/
│       ├── java/         # Java code
│       └── resources/    # Application properties
├── ui/                   # Frontend source files
│   └── password-generator-tool/
│       ├── src/          # React components and services
│       └── public/       # Static assets
└── README.md             # Project documentation
```

## Deployment

The application can be deployed to various cloud platforms:

- **Backend**: Deploy as a Java web service on Render, Heroku, or any Java-supporting platform
- **Frontend**: Deploy on Netlify, Vercel, or any static site hosting service

See the [DEPLOYMENT.md](DEPLOYMENT.md) file for detailed deployment instructions.

## Contributing

Contributions are welcome! If you'd like to contribute:

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is open source and available under the MIT License.

## Acknowledgments

- Created by Ram Sharma
- Built with ❤️ for enhanced online security
- Special thanks to the React and Spring Boot communities

---

© 2025 MySecurePass | Created by Ram Sharma
