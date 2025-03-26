import java.io.*;
import java.util.*;
import java.util.concurrent.*;


// Custom exceptions for handling errors
class InvalidMovieCodeException extends Exception {
    public InvalidMovieCodeException(String message) {
        super(message);
    }
}

class InvalidDateException extends Exception {
    public InvalidDateException(String message) {
        super(message);
    }
}

class OverbookingException extends Exception {
    public OverbookingException(String message) {
        super(message);
    }
}

// Movie class to store movie details
class Movie {
    String code;
    String name;
    Set<String> availableDates; // Unique set of available dates
    Map<String, Integer> availableSeats;
    double price;

    public Movie(String code, String name, double price) {
        this.code = code;
        this.name = name;
        this.availableDates = new HashSet<>();
        this.availableSeats = new HashMap<>();
        this.price = price;
    }
}

// Main Reservation System
class MovieReservationSystem {
    private Map<String, Movie> movies = new HashMap<>();
    private List<String[]> csvData = new ArrayList<>();
    private Scanner scanner = new Scanner(System.in);
    private String csvFilePath;
    private List<String> billEntries = new ArrayList<>();
    private double totalBill = 0.0;
    private String userEmail = ""; // Variable to store the user's email

    // Load movies from CSV file
    public void loadMoviesFromCSV(String filePath) throws IOException {
        csvFilePath = filePath;
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        String line;
        csvData.clear();
        csvData.add(br.readLine().split(",")); // Store header

        while ((line = br.readLine()) != null) {
            String[] data = line.split(",");
            csvData.add(data);
            
            String movieCode = data[0];
            if (!movies.containsKey(movieCode)) {
                movies.put(movieCode, new Movie(movieCode, data[1], Double.parseDouble(data[6])));
            }
            Movie movie = movies.get(movieCode);
            movie.availableDates.add(data[2]); // Store unique dates
            movie.availableSeats.put(data[2] + data[3], Integer.parseInt(data[5]));
        }
        br.close();
    }

    // Update CSV file after booking
    public void updateCSVFile() throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(csvFilePath));
        for (String[] row : csvData) {
            bw.write(String.join(",", row));
            bw.newLine();
        }
        bw.close();
    }

    // Start the booking system
    public void startCashierSystem() {
        billEntries.clear(); // Reset bill details
        totalBill = 0.0; // Reset total bill

        while (true) {
            System.out.print("Enter Movie Code: ");
            String code = scanner.nextLine();
            try {
                if (!movies.containsKey(code)) {
                    throw new InvalidMovieCodeException("Invalid movie code! Please try again.");
                }
            } catch (InvalidMovieCodeException e) {
                System.out.println(e.getMessage());
                continue;
            }
            Movie movie = movies.get(code);

            // Loop until user enters a valid date
            String date;
            while (true) {
                System.out.println("Available Dates: " + String.join(", ", movie.availableDates));
                System.out.print("Enter Date (YYYY-MM-DD): ");
                date = scanner.nextLine();

                if (movie.availableDates.contains(date)) {
                    break;
                } else {
                    System.out.println("Invalid date! Please choose from the available dates.");
                }
            }

            // Get Showtime and validate input
            String showtime;
            while (true) {
                System.out.print("Enter Showtime (Morning/Afternoon/Evening): ");
                showtime = scanner.nextLine();

                // Validating if showtime is one of the allowed values
                if (showtime.equalsIgnoreCase("Morning") || showtime.equalsIgnoreCase("Afternoon") || showtime.equalsIgnoreCase("Evening")) {
                    break;
                } else {
                    System.out.println("Invalid showtime! Please enter Morning, Afternoon, or Evening.");
                }
            }

            String key = date + showtime;

            if (!movie.availableSeats.containsKey(key) || movie.availableSeats.get(key) <= 0) {
                System.out.println("No seats available for this showtime. Please choose another.");
                continue;
            }

            int availableSeats = movie.availableSeats.get(key);
            int quantity;
            while (true) {
                System.out.print("Enter Number of Tickets (Available: " + availableSeats + "): ");
                quantity = scanner.nextInt();
                scanner.nextLine(); // Consume newline
                
                try {
                    if (quantity > availableSeats) {
                        throw new OverbookingException("Not enough seats available.");
                    }
                } catch (OverbookingException e) {
                    System.out.println(e.getMessage());
                    continue;
                }

                if (quantity > 0 && quantity <= availableSeats) {
                    break;
                } else {
                    System.out.println("Invalid number of tickets! Available: " + availableSeats);
                }
            }

            // Temporarily update available seats
            movie.availableSeats.put(key, availableSeats - quantity);
            updateCSVData(code, date, showtime, availableSeats - quantity);
            double totalPrice = quantity * movie.price;
            totalBill += totalPrice;

            // Store bill details
            billEntries.add("Movie: " + movie.name + " | Date: " + date + " | Showtime: " + showtime +
                    " | Tickets: " + quantity + " | Price: $" + totalPrice);

            System.out.print("Confirm Booking? (yes/no): ");
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<String> future = executor.submit(() -> scanner.nextLine());

            try {
                String confirm = future.get(30, TimeUnit.SECONDS);
                executor.shutdown();
                if (confirm.equalsIgnoreCase("no")) {
                    System.out.print("Do you want another booking? (yes/no): ");
                    if (!scanner.nextLine().equalsIgnoreCase("yes")) {
                        System.out.print("Enter your email to receive the bill: ");
                        userEmail = scanner.nextLine();
                        sendEmailWithBill(userEmail); // Send the bill to the email
                        System.out.println("Exiting system. Goodbye!");
                        break;
                    }
                    continue;
                }
            } catch (TimeoutException e) {
                System.out.println("Session timed out! Exiting the system.");
                movie.availableSeats.put(key, availableSeats); // Restore tickets
                updateCSVData(code, date, showtime, availableSeats); // Update CSV
                executor.shutdownNow();
                break;
            } catch (Exception e) {
                executor.shutdownNow();
                System.out.println("An error occurred. Returning to main menu.");
                continue;
            }

            System.out.print("Do you want another booking? (yes/no): ");
            if (!scanner.nextLine().equalsIgnoreCase("yes")) {
                System.out.print("Enter your email to receive the bill: ");
                userEmail = scanner.nextLine();
                sendEmailWithBill(userEmail); // Send the bill to the email
                System.out.println("\nFinal Bill:");
                saveFinalBill();
                System.out.println("Booking saved. Exiting system. Goodbye!");
                break;
            }
        }
    }

    // Update available seats in CSV data
    private void updateCSVData(String code, String date, String showtime, int newSeats) {
        for (String[] row : csvData) {
            if (row[0].equals(code) && row[2].equals(date) && row[3].equals(showtime)) {
                row[5] = String.valueOf(newSeats);
                break;
            }
        }
        try {
            updateCSVFile();
        } catch (IOException e) {
            System.out.println("Error updating CSV file.");
        }
    }

    // Save the final bill before exiting
    private void saveFinalBill() {
        try {
            String filename = "bill.txt";
            BufferedWriter writer = new BufferedWriter(new FileWriter(filename, false));
            writer.write("\n--- Final Bill ---\n");
            for (String entry : billEntries) {
                writer.write(entry);
                writer.newLine();
            }
            writer.write("Total Bill: $" + totalBill);
            writer.newLine();
            writer.write("Email: " + userEmail); // Add email to the final bill
            writer.newLine();
            writer.write("----------------------\n");
            writer.close();
            System.out.println("Final bill saved in " + filename);
        } catch (IOException e) {
            System.out.println("Error saving final bill.");
        }
    }

    // Simulate sending the bill via email (in real applications, integrate with an email service)
    private void sendEmailWithBill(String email) {
        System.out.println("Sending bill to " + email + "...");
        SendEmailpdf sendEmailsystem = new SendEmailpdf();
        sendEmailsystem.main(null);
        // Simulating email sending. In real-world applications, use an email API.
        System.out.println("Bill sent successfully to " + email);
    }
}

// Main class to run the application
public class MovieTicketReservationSystem {
    public static void main(String[] args) {
        MovieReservationSystem system = new MovieReservationSystem();
        try {
            system.loadMoviesFromCSV("movies.csv");
            system.startCashierSystem();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}