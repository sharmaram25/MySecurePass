# Build script for production deployment

# Navigate to the backend directory and build the Spring Boot application
echo "Building backend application..."
cd ..
./mvnw clean package -DskipTests

# Navigate to the frontend directory and build the React application
echo "Building frontend application..."
cd ui/password-generator-tool
npm install
npm run build

echo "Build completed!"
echo "Frontend build output is in ui/password-generator-tool/dist/"
echo "Backend JAR file is in target/password-generator-tool-0.0.1-SNAPSHOT.jar"
