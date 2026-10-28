package dev.hackercode.problem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "topic")
public class Topic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String slug;
    @Column(nullable = false)
    private String name;
    private int position;

    protected Topic() {}

    public Topic(String slug, String name, int position) {
        this.slug = slug;
        this.name = name;
        this.position = position;
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getPosition() { return position; }
}
