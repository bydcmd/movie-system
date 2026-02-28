export type MovieIdLike = {
  id?: number
  movieId?: number
}

export const getMovieId = (movie: MovieIdLike): number | undefined => {
  return movie.id ?? movie.movieId
}
