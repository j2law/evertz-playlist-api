# AI Prompts Used

- For this project, update CLAUDE.md such that it is always using SOLID principles, OOP principles, clean architecture with clear boundaries, DTOs and DAOs to reduce coupling. Let's also use Springboot and the latest version of Java with a Java H2 database. Is there anything else we should consider for best practices before we get started?

- Set up the barebones application according our specs, let's not focus on implementing anything just yet. We will do that next step by step together. Also, apply the YAGNI principle and add this to CLAUDE.md.

- I want to set up the core model according to the specs. Applying clean architecture layering and
  naming conventions.

  - There is an issue with this implementation. The core model should be separate from DTOs and
    DAOs. I want to structure this codebase in 3 separate layers according to clean architecture:
    Infrastructure should consist of the Controller (API) and database. Application layer should
    consist of the service/logic and the persistence interface. The core layer should consist of
    the base entities/model. I only want to implement the core layer in this session.

- Implement the get list items endpoint. Refer back to the specs for the expected response. 
  - The DAO should not enter the application layer eg. conversion
