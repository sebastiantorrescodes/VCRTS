-- Create users table
CREATE TABLE users (
    user_id INT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    roles VARCHAR(200) NOT NULL,
    password VARCHAR(255) NOT NULL 
);

-- Create vehicles table
CREATE TABLE vehicles (
    vin VARCHAR(20) PRIMARY KEY,
    owner_id INT NOT NULL,
    model VARCHAR(50) NOT NULL,
    make VARCHAR(50) NOT NULL,
    year VARCHAR(4) NOT NULL,
    residency_time VARCHAR(10) NOT NULL,
    registered_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- Create jobs table
CREATE TABLE jobs (
    job_id VARCHAR(50) PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL,
    job_owner_id INT NOT NULL,
    duration VARCHAR(10) NOT NULL,
    deadline VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_owner_id) REFERENCES users(user_id) ON DELETE CASCADE
);
