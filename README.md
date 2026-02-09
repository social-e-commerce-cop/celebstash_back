# CelebStash Backend

**CelebStash Backend** is a Java Spring Boot application that powers the backend services for the CelebStash social e-commerce platform. It handles core business logic including user management, authentication, payments, scheduling, and system security using a clean layered architecture.

The project follows a **well-structured Spring Boot architecture**:

##  Features

* **Authentication & Authorization**

  * Secure login using JWT
  * Role-based access control

* **User Management**

  * User registration
  * Password reset functionality

* **Payment Integration**

  * Payment processing support
  * Auditable payment records

* **Scheduled Tasks**

  * Background jobs using Spring Scheduler

* **Audit Logging**

  * Tracks important system actions

* **Exception Handling**

  * Centralized and clean error responses


## Getting Started

### Prerequisites

Make sure you have the following installed:

* Java 17 or higher
* Maven
* PostgreSQL or MySQL
* Git


### Installation

1. **Clone the repository**

   ```bash
   git clone https://github.com/social-e-commerce-cop/celebstash_back.git
   cd celebstash_back
   ```

2. **Configure the database**

   Update `application.properties` or `application.yml`:

   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/celebstash
   spring.datasource.username=your_db_user
   spring.datasource.password=your_db_password

   spring.jpa.hibernate.ddl-auto=update
   spring.jpa.show-sql=true
   ```

3. **Build the project**

   ```bash
   mvn clean install
   ```

4. **Run the application**

   ```bash
   mvn spring-boot:run
   ```

The server will start at:

```
http://localhost:8080
```


## API Documentation

Once the app is running, you can access:

* REST endpoints via controllers
* Swagger UI (if enabled):

  ```
  http://localhost:8080/swagger-ui.html
  ```


## Testing

Run tests using:

```bash
mvn test
```


## Security

* Uses **Spring Security**
* JWT-based authentication
* Password encryption
* Protected routes with role-based access


