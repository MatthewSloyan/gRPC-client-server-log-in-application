package ie.gmit.ds;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

@Path("/users")
@Consumes({ "application/json", "application/xml" })
@Produces({ "application/json", "application/xml" })
public class UserAccountServiceResource {

    private Client passwordClient;
    private HashMap<Integer, User> usersMap = new HashMap<>();
    private final Validator validator;

    public UserAccountServiceResource(int port, Validator validator){
        // Initialise the resource with an instance of the client, this will be used to access it's methods.
        // Set up validator to check if post requests are valid.
        passwordClient = new Client("127.0.0.1", port);
        this.validator = validator;

        // == Add a test user ==.
        // Call the hashPassword method which calls the asynchronous hashPassword method in Client.
        // Then get the hashed password and salt from the client.
        this.hashPassword(1, "test");

        if (passwordClient.getHashedPassword() != null || passwordClient.getSalt() != null){
            User testUser = new User(1, "Test_User", "test@gmail.com",
                    passwordClient.getHashedPassword(), passwordClient.getSalt());
            usersMap.put(1, testUser);

            passwordClient.setNull();
        }
    }

    /**
     * This method gets all users from the HashMap
     *
     * @return  Collection<User> - Returns the Collection<User> as a list.
     */
    @GET
    public Collection<User> getUsers() {
        return usersMap.values();
    }

    /**
     * Get a specific user using the userId passed into the URL.
     *
     * @return  User - Returns a single user if found, otherwise return null.
     */
    @GET
    @Path("{userId}")
    public User getUserById(@PathParam("userId") Integer userId) {
        if(usersMap.containsKey(userId)){
            return usersMap.get(userId);
        }
        else {
            return null;
        }
    }

    /**
     * Adds a new user to the map if valid.
     *
     * @see private void hashPassword(int userId, String userPassword)
     * @return Response - Returns a HTTP response depending if successful or not.
     */
    @POST
    public Response addUser(UserPost userPost){

        // Validates if posted user contains all values, so if the email is not present it will return an error.
        // Code adapted from: https://www.programcreek.com/java-api-examples/javax.validation.ConstraintViolation
        Set<ConstraintViolation<UserPost>> violations = validator.validate(userPost);

        // Check if there is violations or if user already exists
        if(!violations.isEmpty() || usersMap.containsKey(userPost.getUserId())){
            return Response.status(400).type(MediaType.TEXT_PLAIN).entity("User invalid!").build();
        }

        // Call the hashPassword method which calls the asynchronous hashPassword method in Client
        this.hashPassword(userPost.getUserId(), userPost.getPassword());

        // Check if there's any errors on the server side in hashing the password
        if (passwordClient.getHashedPassword() == null || passwordClient.getSalt() == null){
            return Response.status(500).type(MediaType.TEXT_PLAIN).entity("The Server cannot be reached.").build();
        }

        // If all successful create a new user with hashed password and salt
        addUpdateUser(userPost);
        passwordClient.setNull();

        return Response.status(201).type(MediaType.TEXT_PLAIN).entity("User successfully added!").build();
    }

    /**
     * Update a user. If valid then remove old user,
     * and create a new user which creates a new hash and salt for the password in case it's updated.
     *
     * @see private void hashPassword(int userId, String userPassword)
     * @return Response - Returns a HTTP response depending if successful or not.
     */
    @PUT
    @Path("/{userId}")
    public Response updateUser(UserPost user, @PathParam("userId") Integer userId)
    {
        // Validates if posted user contains all values, so if the email is not present it will return an error.
        // Code adapted from: https://www.programcreek.com/java-api-examples/javax.validation.ConstraintViolation
        Set<ConstraintViolation<UserPost>> violations = validator.validate(user);

        // Check if there is violations or if user doesn't exist
        if(!violations.isEmpty() || !usersMap.containsKey(userId)){
            return Response.status(400).type(MediaType.TEXT_PLAIN).entity("User " + userId + " invalid or not found!").build();
        }

        // Call the hashPassword method which calls the asynchronous hashPassword method in Client
        this.hashPassword(user.getUserId(), user.getPassword());

        if (passwordClient.getHashedPassword() == null || passwordClient.getSalt() == null){
            return Response.status(500).type(MediaType.TEXT_PLAIN).entity("The Server cannot be reached.").build();
        }

        // If all successful remove and create a new user with updated user details, hashed password and salt.
        usersMap.remove(userId);
        addUpdateUser(user);
        passwordClient.setNull();

        return Response.status(200).type(MediaType.TEXT_PLAIN).entity("User " + userId + " successfully updated!").build();
    }

    /**
     * Deletes a User from map if valid User.
     *
     * @return Response - Returns a HTTP response depending if successful or not.
     */
    @DELETE
    @Path("/{userId}")
    public Response deleteUser(@PathParam("userId") Integer userId)
    {
        if(usersMap.containsKey(userId)){
            usersMap.remove(userId);
            return Response.status(200).type(MediaType.TEXT_PLAIN).entity("User " + userId + " successfully deleted!").build();
        }
        else {
            return Response.status(400).type(MediaType.TEXT_PLAIN).entity("User " + userId + " not found!").build();
        }
    }

    /**
     * Update a user. If valid then remove old user,
     * and create a new user which creates a new hash and salt for the password in case it's updated.
     *
     * @see private void hashPassword(int userId, String userPassword)
     * @return Response - Returns a HTTP response depending if successful or not.
     */
    @POST
    @Path("/login")
    public Response login(UserLogin userLogin) throws UnsupportedEncodingException {
        String responseMessage;

        // Validates if posted user contains all values, so if the email is not present it will return an error.
        // Code adapted from: https://www.programcreek.com/java-api-examples/javax.validation.ConstraintViolation
        Set<ConstraintViolation<UserLogin>> violations = validator.validate(userLogin);

        // Check if the user exists, if not then no point trying to validate with password.
        if(usersMap.containsKey(userLogin.getUserId()) && violations.isEmpty()){

            // Call the synchronous validate method in Client and return response message
            responseMessage = passwordClient.validatePassword(userLogin.getPassword(),
                    usersMap.get(userLogin.getUserId()).getHashedPassword(),
                    usersMap.get(userLogin.getUserId()).getSalt());

            if (responseMessage == "Successful match"){
                return Response.status(200).type(MediaType.TEXT_PLAIN).entity("Validation Successful.").build();
            }
            else if (responseMessage == "Unsuccessful match"){
                return Response.status(400).type(MediaType.TEXT_PLAIN).entity("User ID or password incorrect.").build();
            }
            else {
                return Response.status(500).type(MediaType.TEXT_PLAIN).entity("The Server cannot be reached.").build();
            }
        }
        else {
            return Response.status(400).type(MediaType.TEXT_PLAIN).entity("User ID or password incorrect.").build();
        }
    }

    /**
     * Creates new User object and adds user to Map. Used in post and put requests
     */
    private void addUpdateUser(UserPost userPost) {
        User newUser = new User(userPost.getUserId(), userPost.getUserName(), userPost.getEmail(),
                passwordClient.getHashedPassword(), passwordClient.getSalt());

        usersMap.put(userPost.getUserId(), newUser);
    }

    /**
     * Creates a hashRequest to send to client asynchronously to update its private instance variables.
     */
    private void hashPassword(int userId, String userPassword)
    {
        // Build a hashRequest object
        HashRequest hashRequest = HashRequest.newBuilder()
                .setUserId(userId)
                .setPassword(userPassword)
                .build();

        passwordClient.hashPassword(hashRequest);
    }
}
