package tv.noirstream;

import androidx.media3.common.util.UnstableApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@UnstableApi
final class StarterCatalog {
    private StarterCatalog() { }

    static List<MainActivity.Title> create() {
        List<MainActivity.Title> titles = new ArrayList<>();
        titles.add(title(693134, "movie", "Dune: Part Two", "2024",
                "Follow the mythic journey of Paul Atreides as he unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.",
                "/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg", "/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
                "Sci-Fi / Adventure", null));
        titles.add(title(872585, "movie", "Oppenheimer", "2023",
                "The story of J. Robert Oppenheimer and his role in the development of the atomic bomb.",
                "/ptpr0kGAckfQkJeJIt8st5dglvd.jpg", "/fm6KqXpk3M2HVveHwCrBSSBaO0V.jpg",
                "Drama / History", null));
        titles.add(title(569094, "movie", "Spider-Man: Across the Spider-Verse", "2023",
                "Miles Morales catapults across the Multiverse, where he encounters a team of Spider-People charged with protecting its existence.",
                "/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg", "/4HodYYKEIsGOdinkGi2Ucz6X9i0.jpg",
                "Animation / Action / Adventure", null));
        titles.add(title(414906, "movie", "The Batman", "2022",
                "When a sadistic serial killer begins murdering key political figures in Gotham, Batman is forced to investigate the city's hidden corruption.",
                "/74xTEgt7R36Fpooo50r9T25onhq.jpg", "/b0PlSFdDwbyK0cf5RxwDpaOJQvQ.jpg",
                "Crime / Mystery / Thriller", null));
        titles.add(title(533535, "movie", "Deadpool & Wolverine", "2024",
                "Deadpool's peaceful existence comes crashing down when the Time Variance Authority recruits him for a mission that changes the history of the Marvel Cinematic Universe.",
                "/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg", "/yDHYTfA3R0jFYba16jBB1ef8oIt.jpg",
                "Action / Comedy / Sci-Fi", null));
        titles.add(title(95479, "tv", "The Last of Us", "2023",
                "Twenty years after modern civilization has been destroyed, Joel is hired to smuggle Ellie out of an oppressive quarantine zone.",
                "/uKvVjHNqB5VmOrdxqAt2F7J78ED.jpg", "/uKvVjHNqB5VmOrdxqAt2F7J78ED.jpg",
                "Drama / Sci-Fi / Adventure", null));
        titles.add(title(94605, "tv", "Arcane", "2021",
                "Amid the stark discord between the twin cities of Piltover and Zaun, two sisters fight on rival sides of a conflict involving powerful technologies.",
                "/fqldf2t8ztc9aiwn3k6mlX3tvRT.jpg", "/rkB4LyZHo1NHXFEDHl9vSD9r1lI.jpg",
                "Animation / Sci-Fi / Action", null));
        titles.add(title(66732, "tv", "Stranger Things", "2016",
                "When a young boy vanishes, a small town uncovers a mystery involving secret experiments, terrifying supernatural forces and one strange little girl.",
                "/49WJfeN0moxb9IPfGn8AIqMGskD.jpg", "/56v2KjBlU4XaOv9rVYEQypROD7P.jpg",
                "Sci-Fi / Drama / Mystery", null));
        titles.add(title(110316, "tv", "The Bear", "2022",
                "A young chef returns to Chicago to run his family's sandwich shop, transforming both the kitchen and himself along the way.",
                "/sHFlbKS3WLqMnp9t2ghADIJFnuQ.jpg", "/zN3lW7b8YiZ4z3w3C8vS1FpBq6Q.jpg",
                "Drama / Comedy", null));
        return titles;
    }

    private static MainActivity.Title title(long id, String type, String name, String year, String overview,
                                            String poster, String backdrop, String genres, Double rating) {
        return new MainActivity.Title(id, type, name, year, overview, poster, backdrop, genres, rating, null);
    }
}
