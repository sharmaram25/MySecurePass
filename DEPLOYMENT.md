# Deployment Guide for Password Generator Tool

This guide provides instructions for deploying both the backend and frontend components of the Password Generator Tool to various cloud platforms.

## Backend Deployment (Spring Boot)

### Option 1: Render

1. Sign up for a Render account at https://render.com
2. Create a new Web Service
3. Connect your GitHub repository
4. Configure as follows:
   - Name: password-generator-api
   - Environment: Java
   - Build Command: `./mvnw clean package -DskipTests`
   - Start Command: `java -jar target/password-generator-tool-0.0.1-SNAPSHOT.jar`
   - Environment Variables:
     - `SPRING_PROFILES_ACTIVE=prod`
     - `SERVER_PORT=8080`
     - `MAIL_USERNAME=your-email@gmail.com`
     - `MAIL_PASSWORD=your-app-password`
5. Click "Create Web Service"

### Option 2: Heroku

1. Sign up for a Heroku account at https://heroku.com
2. Install Heroku CLI: https://devcenter.heroku.com/articles/heroku-cli
3. Login to Heroku: `heroku login`
4. Create a new app: `heroku create password-generator-api`
5. Create a Procfile in the root directory with the content:
   ```
   web: java -jar target/password-generator-tool-0.0.1-SNAPSHOT.jar
   ```
6. Set environment variables:
   ```
   heroku config:set SPRING_PROFILES_ACTIVE=prod
   heroku config:set MAIL_USERNAME=your-email@gmail.com
   heroku config:set MAIL_PASSWORD=your-app-password
   ```
7. Deploy the application:
   ```
   git push heroku main
   ```

## Frontend Deployment (React)

### Option 1: Netlify

1. Sign up for a Netlify account at https://netlify.com
2. Install Netlify CLI: `npm install -g netlify-cli`
3. Login to Netlify: `netlify login`
4. Build the frontend: `cd ui/password-generator-tool && npm run build`
5. Deploy to Netlify: 
   ```
   netlify deploy --prod --dir=ui/password-generator-tool/dist
   ```
   
### Option 2: Vercel

1. Sign up for a Vercel account at https://vercel.com
2. Install Vercel CLI: `npm install -g vercel`
3. Login to Vercel: `vercel login`
4. Navigate to the frontend directory: `cd ui/password-generator-tool`
5. Deploy to Vercel: `vercel --prod`

## Environment Configuration

Make sure to update the API endpoint in the frontend code before building for production:

1. Update the `productionApiUrl` in `ui/password-generator-tool/src/service/PasswordGeneratorApi.js` to match your backend deployment URL.

## Deployment Checklist

- [ ] Backend API is deployed and accessible
- [ ] Frontend is built with the correct API endpoint
- [ ] Email service is properly configured in the backend
- [ ] CORS settings in the backend allow requests from the frontend domain
- [ ] All environment variables are properly set

## Troubleshooting

- If you encounter CORS issues, ensure your backend allows requests from your frontend domain
- For email sending issues, verify your email credentials and app password
- Check logs on the respective platforms for detailed error information
