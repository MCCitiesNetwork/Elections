package net.democracycraft.elections.data;

public class DateDto implements Dto {
    private final int day;
    private final int month;
    private final int year;

    public DateDto(int day, int month, int year) {
        this.day = day;
        this.month = month;
        this.year = year;
    }

    public int getDay() {
        return day;
    }

    public int getMonth() {
        return month;
    }

    public int getYear() {
        return year;
    }
}

